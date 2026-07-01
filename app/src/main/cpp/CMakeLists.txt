cmake_minimum_required(VERSION 3.22.1)

project(lumen_security)

add_library(lumen_security SHARED lumen_security.cpp)

target_compile_definitions(
    lumen_security
    PRIVATE
    LUMEN_REQUEST_SIGNING_SECRET="${LUMEN_REQUEST_SIGNING_SECRET}"
    LUMEN_RELEASE_CERT_SHA256="${LUMEN_RELEASE_CERT_SHA256}"
    LUMEN_EXPECTED_PACKAGE="${LUMEN_EXPECTED_PACKAGE}"
)

find_library(log-lib log)

target_link_libraries(lumen_security ${log-lib})
