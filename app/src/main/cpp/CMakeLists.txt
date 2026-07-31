cmake_minimum_required(VERSION 3.22.1)

project(lumen_security)

add_library(
    lumen_security
    SHARED
    lumen_security.cpp
    lumen_security_crypto.cpp
    lumen_security_identity.cpp
    lumen_security_runtime.cpp
)

target_compile_features(lumen_security PRIVATE cxx_std_17)

if(CMAKE_SYSTEM_NAME STREQUAL "Android")
    target_link_options(lumen_security PRIVATE "-Wl,-z,max-page-size=16384")
endif()

target_compile_definitions(
    lumen_security
    PRIVATE
    LUMEN_REQUEST_SIGNING_SECRET_HEX="${LUMEN_REQUEST_SIGNING_SECRET_HEX}"
    LUMEN_RELEASE_CERT_SHA256="${LUMEN_RELEASE_CERT_SHA256}"
    LUMEN_EXPECTED_PACKAGE="${LUMEN_EXPECTED_PACKAGE}"
)
