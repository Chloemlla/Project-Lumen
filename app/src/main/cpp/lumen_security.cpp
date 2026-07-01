#include <jni.h>

#include <android/log.h>
#include <dirent.h>
#include <fstream>
#include <string>
#include <unistd.h>

#ifndef LUMEN_REQUEST_SIGNING_SECRET
#define LUMEN_REQUEST_SIGNING_SECRET "project-lumen-local-request-signing-key"
#endif

#ifndef LUMEN_RELEASE_CERT_SHA256
#define LUMEN_RELEASE_CERT_SHA256 ""
#endif

#ifndef LUMEN_EXPECTED_PACKAGE
#define LUMEN_EXPECTED_PACKAGE "com.projectlumen.app"
#endif

namespace {

std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool contains_any(const std::string &value, const char *needles[], size_t count) {
    for (size_t i = 0; i < count; ++i) {
        if (value.find(needles[i]) != std::string::npos) return true;
    }
    return false;
}

bool has_tracer_pid() {
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("TracerPid:", 0) == 0) {
            const auto value_start = line.find_first_not_of(" \t", 10);
            if (value_start == std::string::npos) return false;
            return std::stoi(line.substr(value_start)) != 0;
        }
    }
    return false;
}

bool has_hooking_artifacts() {
    static const char *needles[] = {
        "frida",
        "gum-js-loop",
        "gadget",
        "xposed",
        "edxp",
        "lsposed",
        "substrate",
    };

    std::ifstream maps("/proc/self/maps");
    std::string line;
    while (std::getline(maps, line)) {
        if (contains_any(line, needles, sizeof(needles) / sizeof(needles[0]))) return true;
    }

    DIR *task_dir = opendir("/proc/self/task");
    if (task_dir == nullptr) return false;
    bool found = false;
    while (dirent *entry = readdir(task_dir)) {
        if (entry->d_name[0] == '.') continue;
        std::string comm_path = std::string("/proc/self/task/") + entry->d_name + "/comm";
        std::ifstream comm(comm_path);
        std::string name;
        std::getline(comm, name);
        if (contains_any(name, needles, sizeof(needles) / sizeof(needles[0]))) {
            found = true;
            break;
        }
    }
    closedir(task_dir);
    return found;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_projectlumen_app_core_security_NativeSecurityBridge_requestSigningSecret(
    JNIEnv *env,
    jobject /* unused */
) {
    return env->NewStringUTF(LUMEN_REQUEST_SIGNING_SECRET);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_projectlumen_app_core_security_NativeSecurityBridge_isNativeEnvironmentAllowed(
    JNIEnv *env,
    jobject /* unused */,
    jstring package_name,
    jstring signing_cert_sha256,
    jboolean debug_allowed
) {
    const std::string actual_package = jstring_to_string(env, package_name);
    const std::string actual_cert = jstring_to_string(env, signing_cert_sha256);
    const std::string expected_cert = LUMEN_RELEASE_CERT_SHA256;

    if (actual_package != LUMEN_EXPECTED_PACKAGE) return JNI_FALSE;
    if (debug_allowed == JNI_FALSE && expected_cert.empty()) return JNI_FALSE;
    if (!expected_cert.empty() && actual_cert != expected_cert) return JNI_FALSE;
    if (debug_allowed == JNI_FALSE && has_tracer_pid()) return JNI_FALSE;
    if (debug_allowed == JNI_FALSE && has_hooking_artifacts()) return JNI_FALSE;
    return JNI_TRUE;
}
