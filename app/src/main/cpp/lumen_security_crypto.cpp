#include "lumen_security_crypto.h"

#include <algorithm>
#include <array>
#include <cstring>

namespace lumen::security {
namespace {

constexpr std::array<std::uint32_t, 64> kRoundConstants = {
    0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U,
    0x3956c25bU, 0x59f111f1U, 0x923f82a4U, 0xab1c5ed5U,
    0xd807aa98U, 0x12835b01U, 0x243185beU, 0x550c7dc3U,
    0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U, 0xc19bf174U,
    0xe49b69c1U, 0xefbe4786U, 0x0fc19dc6U, 0x240ca1ccU,
    0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU,
    0x983e5152U, 0xa831c66dU, 0xb00327c8U, 0xbf597fc7U,
    0xc6e00bf3U, 0xd5a79147U, 0x06ca6351U, 0x14292967U,
    0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU, 0x53380d13U,
    0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U,
    0xa2bfe8a1U, 0xa81a664bU, 0xc24b8b70U, 0xc76c51a3U,
    0xd192e819U, 0xd6990624U, 0xf40e3585U, 0x106aa070U,
    0x19a4c116U, 0x1e376c08U, 0x2748774cU, 0x34b0bcb5U,
    0x391c0cb3U, 0x4ed8aa4aU, 0x5b9cca4fU, 0x682e6ff3U,
    0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U,
    0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U,
};

struct Sha256Context {
    std::array<std::uint32_t, 8> state = {
        0x6a09e667U,
        0xbb67ae85U,
        0x3c6ef372U,
        0xa54ff53aU,
        0x510e527fU,
        0x9b05688cU,
        0x1f83d9abU,
        0x5be0cd19U,
    };
    std::array<std::uint8_t, 64> block{};
    std::size_t block_size = 0;
    std::uint64_t total_size = 0;
};

std::uint32_t rotate_right(std::uint32_t value, std::uint32_t amount) {
    return (value >> amount) | (value << (32U - amount));
}

void transform(Sha256Context *context, const std::uint8_t *block) {
    std::array<std::uint32_t, 64> words{};
    for (std::size_t index = 0; index < 16; ++index) {
        const std::size_t offset = index * 4;
        words[index] = (static_cast<std::uint32_t>(block[offset]) << 24U) |
            (static_cast<std::uint32_t>(block[offset + 1]) << 16U) |
            (static_cast<std::uint32_t>(block[offset + 2]) << 8U) |
            static_cast<std::uint32_t>(block[offset + 3]);
    }
    for (std::size_t index = 16; index < words.size(); ++index) {
        const std::uint32_t s0 = rotate_right(words[index - 15], 7U) ^
            rotate_right(words[index - 15], 18U) ^
            (words[index - 15] >> 3U);
        const std::uint32_t s1 = rotate_right(words[index - 2], 17U) ^
            rotate_right(words[index - 2], 19U) ^
            (words[index - 2] >> 10U);
        words[index] = words[index - 16] + s0 + words[index - 7] + s1;
    }

    std::uint32_t a = context->state[0];
    std::uint32_t b = context->state[1];
    std::uint32_t c = context->state[2];
    std::uint32_t d = context->state[3];
    std::uint32_t e = context->state[4];
    std::uint32_t f = context->state[5];
    std::uint32_t g = context->state[6];
    std::uint32_t h = context->state[7];

    for (std::size_t index = 0; index < words.size(); ++index) {
        const std::uint32_t sum1 = rotate_right(e, 6U) ^ rotate_right(e, 11U) ^ rotate_right(e, 25U);
        const std::uint32_t choice = (e & f) ^ ((~e) & g);
        const std::uint32_t temp1 = h + sum1 + choice + kRoundConstants[index] + words[index];
        const std::uint32_t sum0 = rotate_right(a, 2U) ^ rotate_right(a, 13U) ^ rotate_right(a, 22U);
        const std::uint32_t majority = (a & b) ^ (a & c) ^ (b & c);
        const std::uint32_t temp2 = sum0 + majority;

        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }

    context->state[0] += a;
    context->state[1] += b;
    context->state[2] += c;
    context->state[3] += d;
    context->state[4] += e;
    context->state[5] += f;
    context->state[6] += g;
    context->state[7] += h;
}

void update(Sha256Context *context, const std::uint8_t *data, std::size_t size) {
    if (size == 0) return;
    context->total_size += size;
    std::size_t offset = 0;
    while (offset < size) {
        const std::size_t writable = std::min(context->block.size() - context->block_size, size - offset);
        std::memcpy(context->block.data() + context->block_size, data + offset, writable);
        context->block_size += writable;
        offset += writable;
        if (context->block_size == context->block.size()) {
            transform(context, context->block.data());
            context->block_size = 0;
        }
    }
}

Sha256Digest finish(Sha256Context *context) {
    const std::uint64_t bit_size = context->total_size * 8U;
    context->block[context->block_size++] = 0x80U;
    if (context->block_size > 56U) {
        std::fill(context->block.begin() + context->block_size, context->block.end(), 0U);
        transform(context, context->block.data());
        context->block_size = 0;
    }
    std::fill(context->block.begin() + context->block_size, context->block.begin() + 56, 0U);
    for (std::size_t index = 0; index < 8; ++index) {
        context->block[63U - index] = static_cast<std::uint8_t>(bit_size >> (index * 8U));
    }
    transform(context, context->block.data());

    Sha256Digest digest{};
    for (std::size_t index = 0; index < context->state.size(); ++index) {
        digest[index * 4] = static_cast<std::uint8_t>(context->state[index] >> 24U);
        digest[index * 4 + 1] = static_cast<std::uint8_t>(context->state[index] >> 16U);
        digest[index * 4 + 2] = static_cast<std::uint8_t>(context->state[index] >> 8U);
        digest[index * 4 + 3] = static_cast<std::uint8_t>(context->state[index]);
    }
    return digest;
}

int hex_value(char value) {
    if (value >= '0' && value <= '9') return value - '0';
    if (value >= 'a' && value <= 'f') return value - 'a' + 10;
    if (value >= 'A' && value <= 'F') return value - 'A' + 10;
    return -1;
}

std::string lower_hex(const Sha256Digest &digest) {
    static constexpr char alphabet[] = "0123456789abcdef";
    std::string output(digest.size() * 2, '0');
    for (std::size_t index = 0; index < digest.size(); ++index) {
        output[index * 2] = alphabet[digest[index] >> 4U];
        output[index * 2 + 1] = alphabet[digest[index] & 0x0fU];
    }
    return output;
}

template <std::size_t Size>
void secure_clear_array(std::array<std::uint8_t, Size> *value) {
    volatile std::uint8_t *cursor = value->data();
    for (std::size_t index = 0; index < value->size(); ++index) cursor[index] = 0U;
}

}  // namespace

Sha256Digest sha256(const std::uint8_t *data, std::size_t size) {
    Sha256Context context;
    update(&context, data, size);
    return finish(&context);
}

std::string hmac_sha256_hex(
    const std::vector<std::uint8_t> &key,
    const std::uint8_t *payload,
    std::size_t payload_size
) {
    constexpr std::size_t block_size = 64;
    std::array<std::uint8_t, block_size> key_block{};
    if (key.size() > block_size) {
        Sha256Digest hashed_key = sha256(key.data(), key.size());
        std::copy(hashed_key.begin(), hashed_key.end(), key_block.begin());
        secure_clear_array(&hashed_key);
    } else if (!key.empty()) {
        std::copy(key.begin(), key.end(), key_block.begin());
    }

    std::array<std::uint8_t, block_size> inner_pad{};
    std::array<std::uint8_t, block_size> outer_pad{};
    for (std::size_t index = 0; index < block_size; ++index) {
        inner_pad[index] = key_block[index] ^ 0x36U;
        outer_pad[index] = key_block[index] ^ 0x5cU;
    }

    Sha256Context inner_context;
    update(&inner_context, inner_pad.data(), inner_pad.size());
    update(&inner_context, payload, payload_size);
    Sha256Digest inner_digest = finish(&inner_context);

    Sha256Context outer_context;
    update(&outer_context, outer_pad.data(), outer_pad.size());
    update(&outer_context, inner_digest.data(), inner_digest.size());
    Sha256Digest digest = finish(&outer_context);
    const std::string output = lower_hex(digest);

    secure_clear_array(&key_block);
    secure_clear_array(&inner_pad);
    secure_clear_array(&outer_pad);
    secure_clear_array(&inner_digest);
    secure_clear_array(&digest);
    return output;
}

bool decode_hex(std::string_view value, std::vector<std::uint8_t> *output) {
    if (output == nullptr || value.empty() || value.size() % 2U != 0U) return false;
    output->assign(value.size() / 2U, 0U);
    for (std::size_t index = 0; index < output->size(); ++index) {
        const int high = hex_value(value[index * 2U]);
        const int low = hex_value(value[index * 2U + 1U]);
        if (high < 0 || low < 0) {
            secure_clear(output);
            return false;
        }
        (*output)[index] = static_cast<std::uint8_t>((high << 4) | low);
    }
    return true;
}

void secure_clear(std::vector<std::uint8_t> *value) {
    if (value == nullptr) return;
    volatile std::uint8_t *cursor = value->data();
    for (std::size_t index = 0; index < value->size(); ++index) cursor[index] = 0U;
    value->clear();
}

}  // namespace lumen::security
