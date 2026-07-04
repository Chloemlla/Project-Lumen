#include <jni.h>

#include <android/log.h>
#include <array>
#include <cctype>
#include <cstdlib>
#include <dirent.h>
#include <fstream>
#include <limits.h>
#include <sstream>
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

extern "C" char **environ;

namespace {

std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string lower_ascii(std::string value) {
    for (char &character : value) {
        character = static_cast<char>(std::tolower(static_cast<unsigned char>(character)));
    }
    return value;
}

template <size_t Count>
bool contains_any(const std::string &value, const std::array<const char *, Count> &needles) {
    const std::string normalized = lower_ascii(value);
    for (const char *needle : needles) {
        if (normalized.find(needle) != std::string::npos) return true;
    }
    return false;
}

std::string read_text_file(const std::string &path, size_t limit = 8192) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return "";
    std::ostringstream buffer;
    buffer << file.rdbuf();
    std::string content = buffer.str();
    if (content.size() > limit) content.resize(limit);
    for (char &character : content) {
        if (character == '\0') character = '\n';
    }
    return content;
}

std::string read_symlink_target(const std::string &path) {
    char target[PATH_MAX] = {};
    const ssize_t length = readlink(path.c_str(), target, sizeof(target) - 1);
    if (length <= 0) return "";
    target[length] = '\0';
    return std::string(target);
}

template <size_t Count>
bool scan_task_comm_for_artifacts(const std::array<const char *, Count> &needles) {
    DIR *task_dir = opendir("/proc/self/task");
    if (task_dir == nullptr) return false;
    bool found = false;
    while (dirent *entry = readdir(task_dir)) {
        if (entry->d_name[0] == '.') continue;
        const std::string comm_path = std::string("/proc/self/task/") + entry->d_name + "/comm";
        if (contains_any(read_text_file(comm_path), needles)) {
            found = true;
            break;
        }
    }
    closedir(task_dir);
    return found;
}

template <size_t Count>
bool scan_fd_targets_for_artifacts(const std::array<const char *, Count> &needles) {
    DIR *fd_dir = opendir("/proc/self/fd");
    if (fd_dir == nullptr) return false;
    bool found = false;
    while (dirent *entry = readdir(fd_dir)) {
        if (entry->d_name[0] == '.') continue;
        const std::string fd_path = std::string("/proc/self/fd/") + entry->d_name;
        if (contains_any(read_symlink_target(fd_path), needles)) {
            found = true;
            break;
        }
    }
    closedir(fd_dir);
    return found;
}

template <size_t Count>
bool scan_text_file_for_artifacts(
    const std::string &path,
    const std::array<const char *, Count> &needles,
    size_t limit = 128 * 1024
) {
    return contains_any(read_text_file(path, limit), needles);
}

bool has_debug_environment() {
    static constexpr std::array<const char *, 5> environment_keys = {
        "ld_preload",
        "ld_audit",
        "frida",
        "xposed",
        "substrate",
    };

    for (char **current = environ; current != nullptr && *current != nullptr; ++current) {
        if (contains_any(*current, environment_keys)) return true;
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
            try {
                return std::stoi(line.substr(value_start)) != 0;
            } catch (...) {
                return false;
            }
        }
    }
    return false;
}

bool has_hooking_artifacts() {
    static constexpr std::array<const char *, 15> needles = {
        "frida",
        "frida-agent",
        "frida-gadget",
        "frida-server",
        "gum-js-loop",
        "gadget",
        "gmain",
        "linjector",
        "riru",
        "zygisk",
        "xposed",
        "edxp",
        "lsposed",
        "substrate",
        "substrate-loader",
    };

    return scan_text_file_for_artifacts("/proc/self/maps", needles) ||
        scan_text_file_for_artifacts("/proc/self/cmdline", needles) ||
        scan_text_file_for_artifacts("/proc/net/unix", needles) ||
        scan_task_comm_for_artifacts(needles) ||
        scan_fd_targets_for_artifacts(needles);
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
    if (!expected_cert.empty() && actual_cert != expected_cert) return JNI_FALSE;
    if (debug_allowed == JNI_FALSE && has_tracer_pid()) return JNI_FALSE;
    if (debug_allowed == JNI_FALSE && has_debug_environment()) return JNI_FALSE;
    if (debug_allowed == JNI_FALSE && has_hooking_artifacts()) return JNI_FALSE;
    return JNI_TRUE;
}
