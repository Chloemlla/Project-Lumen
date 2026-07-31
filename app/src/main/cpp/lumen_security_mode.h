#pragma once

#ifndef LUMEN_NATIVE_RELEASE_BUILD
// A direct native build is production-like unless the build system explicitly opts
// into debug mode. Android Gradle always passes 0 or 1 for this definition.
#define LUMEN_NATIVE_RELEASE_BUILD 1
#endif

namespace lumen::security {

constexpr bool kNativeReleaseBuild = LUMEN_NATIVE_RELEASE_BUILD != 0;

constexpr bool effective_debug_allowed(bool requested) {
    return requested && !kNativeReleaseBuild;
}

}  // namespace lumen::security
