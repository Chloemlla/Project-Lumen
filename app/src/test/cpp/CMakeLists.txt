cmake_minimum_required(VERSION 3.22.1)

project(lumen_security_native_tests)

enable_testing()

add_executable(
    lumen_security_native_tests
    lumen_security_native_tests.cpp
    ../../main/cpp/lumen_security_crypto.cpp
    ../../main/cpp/lumen_security_identity.cpp
)

target_compile_features(lumen_security_native_tests PRIVATE cxx_std_17)
target_include_directories(lumen_security_native_tests PRIVATE ../../main/cpp)

add_test(NAME lumen_security_native_tests COMMAND lumen_security_native_tests)
