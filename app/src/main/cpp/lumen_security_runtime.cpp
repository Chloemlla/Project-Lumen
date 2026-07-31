#include "lumen_security_runtime.h"

#include "lumen_security_reasons.h"

#include <array>
#include <cerrno>
#include <cctype>
#include <dirent.h>
#include <fstream>
#include <limits.h>
#include <string>
#include <unistd.h>

extern "C" char **environ;

namespace lumen::security {
namespace {

enum class DetectionState {
    kClean,
    kDetected,
    kError,
};

std::string lower_ascii(std::string value) {
    for (char &character : value) {
        character = static_cast<char>(std::tolower(static_cast<unsigned char>(character)));
    }
    return value;
}

template <std::size_t Count>
bool contains_any(const std::string &value, const std::array<const char *, Count> &needles) {
    const std::string normalized = lower_ascii(value);
    for (const char *needle : needles) {
        if (normalized.find(needle) != std::string::npos) return true;
    }
    return false;
}

template <std::size_t Count>
DetectionState scan_text_file(
    const char *path,
    const std::array<const char *, Count> &needles,
    std::size_t byte_limit
) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return DetectionState::kError;
    std::size_t bytes_read = 0;
    std::string line;
    while (std::getline(file, line)) {
        bytes_read += line.size() + 1U;
        if (bytes_read > byte_limit) return DetectionState::kError;
        if (contains_any(line, needles)) return DetectionState::kDetected;
    }
    return file.bad() ? DetectionState::kError : DetectionState::kClean;
}

DetectionState detect_tracer() {
    constexpr std::size_t status_limit = 64U * 1024U;
    std::ifstream file("/proc/self/status");
    if (!file) return DetectionState::kError;
    std::size_t bytes_read = 0;
    std::string line;
    while (std::getline(file, line)) {
        bytes_read += line.size() + 1U;
        if (bytes_read > status_limit) return DetectionState::kError;
        if (line.rfind("TracerPid:", 0) != 0) continue;
        const std::size_t start = line.find_first_not_of(" \t", 10U);
        if (start == std::string::npos) return DetectionState::kError;
        bool non_zero = false;
        bool saw_digit = false;
        for (std::size_t index = start; index < line.size(); ++index) {
            const char character = line[index];
            if (character == ' ' || character == '\t' || character == '\r') continue;
            if (character < '0' || character > '9') return DetectionState::kError;
            saw_digit = true;
            non_zero = non_zero || character != '0';
        }
        if (!saw_digit) return DetectionState::kError;
        return non_zero ? DetectionState::kDetected : DetectionState::kClean;
    }
    return DetectionState::kError;
}

std::size_t bounded_length(const char *value, std::size_t limit) {
    if (value == nullptr) return 0;
    std::size_t length = 0;
    while (length < limit && value[length] != '\0') ++length;
    return length;
}

DetectionState detect_suspicious_environment() {
    static constexpr std::array<const char *, 5> needles = {
        "ld_preload=",
        "ld_audit=",
        "frida",
        "xposed",
        "substrate",
    };
    constexpr std::size_t max_entries = 512;
    constexpr std::size_t max_entry_bytes = 4096;
    if (environ == nullptr) return DetectionState::kError;
    for (std::size_t index = 0; index < max_entries; ++index) {
        const char *entry = environ[index];
        if (entry == nullptr) return DetectionState::kClean;
        const std::size_t length = bounded_length(entry, max_entry_bytes);
        if (length == max_entry_bytes) return DetectionState::kError;
        if (contains_any(std::string(entry, length), needles)) return DetectionState::kDetected;
    }
    return DetectionState::kError;
}

std::string read_symlink_target(const std::string &path) {
    std::array<char, PATH_MAX> target{};
    const ssize_t length = readlink(path.c_str(), target.data(), target.size() - 1U);
    if (length <= 0 || static_cast<std::size_t>(length) >= target.size() - 1U) return {};
    return std::string(target.data(), static_cast<std::size_t>(length));
}

template <std::size_t Count>
DetectionState scan_task_names(const std::array<const char *, Count> &needles) {
    constexpr std::size_t max_tasks = 512;
    DIR *directory = opendir("/proc/self/task");
    if (directory == nullptr) return DetectionState::kError;
    DetectionState result = DetectionState::kClean;
    std::size_t count = 0;
    while (true) {
        errno = 0;
        dirent *entry = readdir(directory);
        if (entry == nullptr) {
            if (errno != 0) result = DetectionState::kError;
            break;
        }
        if (entry->d_name[0] == '.') continue;
        if (++count > max_tasks) {
            result = DetectionState::kError;
            break;
        }
        const std::string path = std::string("/proc/self/task/") + entry->d_name + "/comm";
        const DetectionState state = scan_text_file(path.c_str(), needles, 4096U);
        if (state == DetectionState::kDetected) {
            result = DetectionState::kDetected;
            break;
        }
    }
    closedir(directory);
    return result;
}

template <std::size_t Count>
DetectionState scan_file_descriptors(const std::array<const char *, Count> &needles) {
    constexpr std::size_t max_descriptors = 1024;
    DIR *directory = opendir("/proc/self/fd");
    if (directory == nullptr) return DetectionState::kError;
    DetectionState result = DetectionState::kClean;
    std::size_t count = 0;
    while (true) {
        errno = 0;
        dirent *entry = readdir(directory);
        if (entry == nullptr) {
            if (errno != 0) result = DetectionState::kError;
            break;
        }
        if (entry->d_name[0] == '.') continue;
        if (++count > max_descriptors) {
            result = DetectionState::kError;
            break;
        }
        const std::string path = std::string("/proc/self/fd/") + entry->d_name;
        const std::string target = read_symlink_target(path);
        if (!target.empty() && contains_any(target, needles)) {
            result = DetectionState::kDetected;
            break;
        }
    }
    closedir(directory);
    return result;
}

DetectionState detect_hook_artifacts() {
    static constexpr std::array<const char *, 10> mapped_artifacts = {
        "frida-agent",
        "frida-gadget",
        "frida-server",
        "libfrida",
        "libxposed",
        "lsposed",
        "edxp",
        "substrate",
        "riru",
        "zygisk",
    };
    static constexpr std::array<const char *, 5> task_artifacts = {
        "gum-js-loop",
        "frida",
        "linjector",
        "xposed",
        "substrate",
    };
    static constexpr std::array<const char *, 6> transport_artifacts = {
        "frida",
        "linjector",
        "xposed",
        "lsposed",
        "substrate",
        "zygisk",
    };

    const std::array<DetectionState, 5> states = {
        scan_text_file("/proc/self/maps", mapped_artifacts, 4U * 1024U * 1024U),
        scan_text_file("/proc/self/cmdline", mapped_artifacts, 4096U),
        scan_text_file("/proc/net/unix", transport_artifacts, 1024U * 1024U),
        scan_task_names(task_artifacts),
        scan_file_descriptors(transport_artifacts),
    };
    bool has_error = false;
    for (const DetectionState state : states) {
        if (state == DetectionState::kDetected) return DetectionState::kDetected;
        has_error = has_error || state == DetectionState::kError;
    }
    return has_error ? DetectionState::kError : DetectionState::kClean;
}

void add_detection_reason(
    DetectionState state,
    std::uint32_t detected_reason,
    std::uint32_t *reasons
) {
    if (state == DetectionState::kDetected) *reasons |= detected_reason;
    if (state == DetectionState::kError) *reasons |= kInternalFailure;
}

}  // namespace

std::uint32_t collect_volatile_reasons() {
    std::uint32_t reasons = 0;
    add_detection_reason(detect_tracer(), kTracerDetected, &reasons);
    add_detection_reason(
        detect_suspicious_environment(),
        kSuspiciousEnvironment,
        &reasons
    );
    add_detection_reason(detect_hook_artifacts(), kHookArtifactDetected, &reasons);
    return reasons;
}

}  // namespace lumen::security
