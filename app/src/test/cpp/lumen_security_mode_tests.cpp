#include "lumen_security_mode.h"

#include <cstdlib>
#include <iostream>

int main() {
    if (!lumen::security::kNativeReleaseBuild ||
        lumen::security::effective_debug_allowed(true) ||
        lumen::security::effective_debug_allowed(false)) {
        std::cerr << "Release native mode accepted a runtime debug override\n";
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}
