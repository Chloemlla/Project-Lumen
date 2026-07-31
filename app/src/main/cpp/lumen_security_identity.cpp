#include "lumen_security_identity.h"

#include <cctype>
#include <fstream>
#include <utility>

namespace lumen::security {
namespace {

bool is_separator(char value) {
    return value == ':' || value == '-' ||
        std::isspace(static_cast<unsigned char>(value)) != 0;
}

char lower_hex(char value) {
    if (value >= '0' && value <= '9') return value;
    if (value >= 'a' && value <= 'f') return value;
    if (value >= 'A' && value <= 'F') return static_cast<char>(value - 'A' + 'a');
    return '\0';
}

}  // namespace

NormalizedFingerprint normalize_certificate_sha256(std::string_view value) {
    std::string normalized;
    normalized.reserve(64);
    for (const char character : value) {
        if (is_separator(character)) continue;
        const char hex = lower_hex(character);
        if (hex == '\0') return {FingerprintState::kInvalid, {}};
        normalized.push_back(hex);
    }
    if (normalized.empty()) return {FingerprintState::kMissing, {}};
    if (normalized.size() != 64U) return {FingerprintState::kInvalid, {}};
    return {FingerprintState::kValid, std::move(normalized)};
}

ProcessNameResult read_process_name() {
    constexpr std::size_t max_process_name_bytes = 256;
    std::ifstream file("/proc/self/cmdline", std::ios::binary);
    if (!file) return {false, {}};

    std::string value(max_process_name_bytes, '\0');
    file.read(value.data(), static_cast<std::streamsize>(value.size()));
    const std::streamsize count = file.gcount();
    if (count <= 0 || file.bad()) return {false, {}};
    value.resize(static_cast<std::size_t>(count));
    const std::size_t terminator = value.find('\0');
    if (terminator != std::string::npos) {
        value.resize(terminator);
    } else if (static_cast<std::size_t>(count) == max_process_name_bytes) {
        return {false, {}};
    }
    if (value.empty()) return {false, {}};
    return {true, std::move(value)};
}

}  // namespace lumen::security
