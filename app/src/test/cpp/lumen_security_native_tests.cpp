#include "lumen_security_crypto.h"
#include "lumen_security_identity.h"
#include "lumen_security_sockets.h"

#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

namespace {

std::vector<std::uint8_t> bytes(const std::string &value) {
    return {value.begin(), value.end()};
}

void require(bool condition, const char *message) {
    if (condition) return;
    std::cerr << message << '\n';
    std::exit(1);
}

void verifies_rfc_4231_hmac_vector() {
    const std::vector<std::uint8_t> key(20, 0x0bU);
    const std::vector<std::uint8_t> payload = bytes("Hi There");
    require(
        lumen::security::hmac_sha256_hex(key, payload.data(), payload.size()) ==
        "b0344c61d8db38535ca8afceaf0bf12b"
        "881dc200c9833da726e9376c2e32cff7",
        "RFC 4231 HMAC vector did not match"
    );
}

void verifies_rfc_4231_long_key_vector() {
    const std::vector<std::uint8_t> key(131, 0xaaU);
    const std::vector<std::uint8_t> payload = bytes(
        "Test Using Larger Than Block-Size Key - Hash Key First"
    );
    require(
        lumen::security::hmac_sha256_hex(key, payload.data(), payload.size()) ==
        "60e431591ee0b67f0d8a26aacbf5b77f"
        "8e0bc6213728c5140546040f0ee37f54",
        "RFC 4231 long-key HMAC vector did not match"
    );
}

void verifies_project_lumen_canonical_vector() {
    const std::vector<std::uint8_t> key = bytes("project-lumen-local-request-signing-key");
    const std::vector<std::uint8_t> payload = bytes(
        "bodySha256=015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862\n"
        "method=POST\n"
        "nonce=00112233445566778899aabbccddeeff\n"
        "path=/api/v1/sync/push\n"
        "query=channel=stable&cursor=7\n"
        "timestamp=1720000000"
    );
    require(
        lumen::security::hmac_sha256_hex(key, payload.data(), payload.size()) ==
        "f9dee2240fc31dd902899ed4cba47fc6"
        "afbc8793660fd89c9af54a2155370cf2",
        "Project Lumen canonical HMAC vector did not match"
    );
}

void decodes_the_utf8_hex_fixture() {
    std::vector<std::uint8_t> decoded;
    require(
        lumen::security::decode_hex(
            "70726f6a6563742d6c756d656e2d6c6f63616c2d726571756573742d7369676e696e672d6b6579",
            &decoded
        ),
        "UTF-8 hex fixture could not be decoded"
    );
    require(
        decoded == bytes("project-lumen-local-request-signing-key"),
        "UTF-8 hex fixture decoded to different bytes"
    );
    lumen::security::secure_clear(&decoded);
}

void normalizes_certificate_fingerprints() {
    const auto normalized = lumen::security::normalize_certificate_sha256(
        "AA:BB-CC DD EE FF 00 11 22 33 44 55 66 77 88 99 "
        "AA BB CC DD EE FF 00 11 22 33 44 55 66 77 88 99"
    );
    require(
        normalized.state == lumen::security::FingerprintState::kValid,
        "Certificate fingerprint should be valid"
    );
    require(
        normalized.value == "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
        "Certificate fingerprint normalization changed its bytes"
    );
    require(
        lumen::security::normalize_certificate_sha256("").state ==
        lumen::security::FingerprintState::kMissing,
        "Empty certificate fingerprint should be missing"
    );
    require(
        lumen::security::normalize_certificate_sha256("not-a-certificate").state ==
        lumen::security::FingerprintState::kInvalid,
        "Malformed certificate fingerprint should be invalid"
    );
}

void limits_unix_socket_matches_to_owned_descriptors() {
    require(
        lumen::security::socket_inode_from_fd_target("socket:[4242]") == "4242",
        "Socket fd target inode was not parsed"
    );
    require(
        lumen::security::socket_inode_from_fd_target("/tmp/frida.sock").empty(),
        "Non-socket fd target was misclassified"
    );
    const std::string suspicious_line =
        "0000000000000000: 00000002 00000000 00010000 0001 01 4242 @frida-server";
    require(
        lumen::security::unix_socket_line_is_owned(suspicious_line, {"4242"}),
        "Owned suspicious socket line was not recognized"
    );
    require(
        !lumen::security::unix_socket_line_is_owned(suspicious_line, {"9999"}),
        "A different process socket inode was treated as owned"
    );
}

}  // namespace

int main() {
    verifies_rfc_4231_hmac_vector();
    verifies_rfc_4231_long_key_vector();
    verifies_project_lumen_canonical_vector();
    decodes_the_utf8_hex_fixture();
    normalizes_certificate_fingerprints();
    limits_unix_socket_matches_to_owned_descriptors();
    return 0;
}
