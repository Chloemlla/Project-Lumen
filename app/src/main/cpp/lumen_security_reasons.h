#pragma once

#include <cstdint>

namespace lumen::security {

enum Reason : std::uint32_t {
    kPackageMismatch = 1U << 0U,
    kProcessNameMismatch = 1U << 1U,
    kCertificateMissing = 1U << 2U,
    kCertificateMismatch = 1U << 3U,
    kTracerDetected = 1U << 4U,
    kSuspiciousEnvironment = 1U << 5U,
    kHookArtifactDetected = 1U << 6U,
    kReleaseIdentityNotVerified = 1U << 7U,
    kSigningSecretInvalid = 1U << 8U,
    kInternalFailure = 1U << 9U,
};

}  // namespace lumen::security
