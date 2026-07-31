cmake_minimum_required(VERSION 3.22.1)

project(lumen_security_native_tests)

enable_testing()

add_executable(
    lumen_security_native_tests
    lumen_security_native_tests.cpp
    ../../main/cpp/lumen_security_crypto.cpp
    ../../main/cpp/lumen_security_identity.cpp
    ../../main/cpp/lumen_security_sockets.cpp
)

target_compile_features(lumen_security_native_tests PRIVATE cxx_std_17)
target_include_directories(lumen_security_native_tests PRIVATE ../../main/cpp)
target_compile_definitions(lumen_security_native_tests PRIVATE LUMEN_NATIVE_RELEASE_BUILD=0)

add_test(NAME lumen_security_native_tests COMMAND lumen_security_native_tests)

add_executable(
    lumen_security_native_release_mode_tests
    lumen_security_mode_tests.cpp
)
target_compile_features(lumen_security_native_release_mode_tests PRIVATE cxx_std_17)
target_include_directories(lumen_security_native_release_mode_tests PRIVATE ../../main/cpp)
target_compile_definitions(lumen_security_native_release_mode_tests PRIVATE LUMEN_NATIVE_RELEASE_BUILD=1)
add_test(
    NAME lumen_security_native_release_mode_tests
    COMMAND lumen_security_native_release_mode_tests
)
