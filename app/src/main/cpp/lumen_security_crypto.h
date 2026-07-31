#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace lumen::security {

using Sha256Digest = std::array<std::uint8_t, 32>;

Sha256Digest sha256(const std::uint8_t *data, std::size_t size);

std::string hmac_sha256_hex(
    const std::vector<std::uint8_t> &key,
    const std::uint8_t *payload,
    std::size_t payload_size
);

bool decode_hex(std::string_view value, std::vector<std::uint8_t> *output);

void secure_clear(std::vector<std::uint8_t> *value);

}  // namespace lumen::security
