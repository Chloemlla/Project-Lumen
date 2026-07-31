#pragma once

#include <string>
#include <string_view>

namespace lumen::security {

enum class FingerprintState {
    kMissing,
    kInvalid,
    kValid,
};

struct NormalizedFingerprint {
    FingerprintState state;
    std::string value;
};

struct ProcessNameResult {
    bool success;
    std::string value;
};

NormalizedFingerprint normalize_certificate_sha256(std::string_view value);

ProcessNameResult read_process_name();

}  // namespace lumen::security
