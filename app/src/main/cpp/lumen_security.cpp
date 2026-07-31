#include <jni.h>

#include "lumen_security_crypto.h"
#include "lumen_security_identity.h"
#include "lumen_security_reasons.h"
#include "lumen_security_runtime.h"

#include <atomic>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#ifndef LUMEN_REQUEST_SIGNING_SECRET_HEX
#define LUMEN_REQUEST_SIGNING_SECRET_HEX \
    "70726f6a6563742d6c756d656e2d6c6f63616c2d726571756573742d7369676e696e672d6b6579"
#endif

#ifndef LUMEN_RELEASE_CERT_SHA256
#define LUMEN_RELEASE_CERT_SHA256 ""
#endif

#ifndef LUMEN_EXPECTED_PACKAGE
#define LUMEN_EXPECTED_PACKAGE "com.chloemlla.projectlumen"
#endif

namespace {

constexpr jsize kMaximumCanonicalPayloadBytes = 128 * 1024;
std::atomic_bool release_identity_verified{false};

std::string jstring_to_string(JNIEnv *env, jstring value, bool *success) {
    if (value == nullptr) {
        *success = false;
        return {};
    }
    const char *characters = env->GetStringUTFChars(value, nullptr);
    if (characters == nullptr || env->ExceptionCheck() == JNI_TRUE) {
        *success = false;
        return {};
    }
    std::string result(characters);
    env->ReleaseStringUTFChars(value, characters);
    return result;
}

std::uint32_t evaluate_environment(
    const std::string &actual_package,
    const std::string &actual_certificate,
    bool debug_allowed
) {
    using namespace lumen::security;
    std::uint32_t reasons = 0;
    const std::string expected_package = LUMEN_EXPECTED_PACKAGE;
    if (expected_package.empty()) reasons |= kInternalFailure;
    if (actual_package != expected_package) reasons |= kPackageMismatch;

    const ProcessNameResult process_name = read_process_name();
    if (!process_name.success) {
        reasons |= kInternalFailure;
    } else if (process_name.value != expected_package) {
        reasons |= kProcessNameMismatch;
    }

    const NormalizedFingerprint expected_certificate =
        normalize_certificate_sha256(LUMEN_RELEASE_CERT_SHA256);
    const NormalizedFingerprint normalized_actual_certificate =
        normalize_certificate_sha256(actual_certificate);
    if (!debug_allowed || expected_certificate.state != FingerprintState::kMissing) {
        if (expected_certificate.state == FingerprintState::kMissing ||
            normalized_actual_certificate.state == FingerprintState::kMissing) {
            reasons |= kCertificateMissing;
        } else if (expected_certificate.state != FingerprintState::kValid ||
                   normalized_actual_certificate.state != FingerprintState::kValid ||
                   expected_certificate.value != normalized_actual_certificate.value) {
            reasons |= kCertificateMismatch;
        }
        if (expected_certificate.state == FingerprintState::kInvalid) {
            reasons |= kInternalFailure;
        }
    }

    if (!debug_allowed) reasons |= collect_volatile_reasons();
    release_identity_verified.store(
        !debug_allowed && reasons == 0,
        std::memory_order_release
    );
    return reasons;
}

bool read_payload(JNIEnv *env, jbyteArray payload, std::vector<std::uint8_t> *output) {
    if (payload == nullptr || output == nullptr) return false;
    const jsize length = env->GetArrayLength(payload);
    if (env->ExceptionCheck() == JNI_TRUE || length < 0 || length > kMaximumCanonicalPayloadBytes) {
        return false;
    }
    output->assign(static_cast<std::size_t>(length), 0U);
    if (length > 0) {
        env->GetByteArrayRegion(
            payload,
            0,
            length,
            reinterpret_cast<jbyte *>(output->data())
        );
    }
    return env->ExceptionCheck() == JNI_FALSE;
}

bool write_reason_mask(JNIEnv *env, jintArray output, std::uint32_t reasons) {
    if (output == nullptr || env->GetArrayLength(output) < 1) return false;
    const jint value = static_cast<jint>(reasons);
    env->SetIntArrayRegion(output, 0, 1, &value);
    return env->ExceptionCheck() == JNI_FALSE;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_projectlumen_app_core_security_NativeSecurityBridge_evaluateEnvironment(
    JNIEnv *env,
    jobject /* unused */,
    jstring package_name,
    jstring signing_cert_sha256,
    jboolean debug_allowed
) {
    using namespace lumen::security;
    try {
        bool package_success = true;
        bool certificate_success = true;
        const std::string actual_package =
            jstring_to_string(env, package_name, &package_success);
        const std::string actual_certificate =
            jstring_to_string(env, signing_cert_sha256, &certificate_success);
        std::uint32_t reasons = evaluate_environment(
            actual_package,
            actual_certificate,
            debug_allowed == JNI_TRUE
        );
        if (!package_success || !certificate_success) reasons |= kInternalFailure;
        if (debug_allowed == JNI_FALSE && reasons != 0) {
            release_identity_verified.store(false, std::memory_order_release);
        }
        return static_cast<jint>(reasons);
    } catch (...) {
        release_identity_verified.store(false, std::memory_order_release);
        return static_cast<jint>(kInternalFailure);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_projectlumen_app_core_security_NativeSecurityBridge_signCanonicalPayload(
    JNIEnv *env,
    jobject /* unused */,
    jbyteArray canonical_payload_utf8,
    jboolean debug_allowed,
    jintArray reason_mask_out
) {
    using namespace lumen::security;
    std::uint32_t reasons = 0;
    std::vector<std::uint8_t> secret;
    std::vector<std::uint8_t> payload;
    try {
        const bool is_debug_allowed = debug_allowed == JNI_TRUE;
        if (!is_debug_allowed &&
            !release_identity_verified.load(std::memory_order_acquire)) {
            reasons |= kReleaseIdentityNotVerified;
        }
        if (!is_debug_allowed) reasons |= collect_volatile_reasons();
        if (!decode_hex(std::string_view(LUMEN_REQUEST_SIGNING_SECRET_HEX), &secret) ||
            secret.empty()) {
            reasons |= kSigningSecretInvalid;
        }
        if (!read_payload(env, canonical_payload_utf8, &payload)) {
            reasons |= kInternalFailure;
        }
        if (reasons != 0) {
            write_reason_mask(env, reason_mask_out, reasons);
            secure_clear(&secret);
            secure_clear(&payload);
            return nullptr;
        }

        const std::string signature = hmac_sha256_hex(
            secret,
            payload.data(),
            payload.size()
        );
        secure_clear(&secret);
        secure_clear(&payload);
        if (signature.size() != 64U || !write_reason_mask(env, reason_mask_out, 0)) {
            return nullptr;
        }
        return env->NewStringUTF(signature.c_str());
    } catch (...) {
        reasons |= kInternalFailure;
        write_reason_mask(env, reason_mask_out, reasons);
        secure_clear(&secret);
        secure_clear(&payload);
        return nullptr;
    }
}
