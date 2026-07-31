#include "lumen_security_sockets.h"

#include <algorithm>
#include <cctype>
#include <fstream>

namespace lumen::security {
namespace {

constexpr std::size_t kMaximumUnixSocketBytes = 1024U * 1024U;
constexpr std::size_t kMaximumUnixSocketLines = 4096U;

bool is_decimal(std::string_view value) {
    return !value.empty() && std::all_of(value.begin(), value.end(), [](char character) {
        return character >= '0' && character <= '9';
    });
}

std::string_view next_token(std::string_view line, std::size_t *cursor) {
    while (*cursor < line.size() &&
           std::isspace(static_cast<unsigned char>(line[*cursor])) != 0) {
        ++*cursor;
    }
    const std::size_t start = *cursor;
    while (*cursor < line.size() &&
           std::isspace(static_cast<unsigned char>(line[*cursor])) == 0) {
        ++*cursor;
    }
    return line.substr(start, *cursor - start);
}

std::string unix_socket_inode_from_line(std::string_view line) {
    std::size_t cursor = 0;
    std::string_view token;
    for (std::size_t column = 0; column < 7U; ++column) {
        token = next_token(line, &cursor);
        if (token.empty()) return {};
    }
    return is_decimal(token) ? std::string(token) : std::string{};
}

bool contains_case_insensitive(
    std::string_view value,
    const std::vector<std::string_view> &needles
) {
    std::string normalized(value);
    std::transform(
        normalized.begin(),
        normalized.end(),
        normalized.begin(),
        [](char character) {
            return static_cast<char>(
                std::tolower(static_cast<unsigned char>(character))
            );
        }
    );
    return std::any_of(needles.begin(), needles.end(), [&](std::string_view needle) {
        return normalized.find(needle) != std::string::npos;
    });
}

}  // namespace

std::string socket_inode_from_fd_target(std::string_view target) {
    constexpr std::string_view prefix = "socket:[";
    if (target.size() <= prefix.size() + 1U ||
        target.substr(0, prefix.size()) != prefix ||
        target.back() != ']') {
        return {};
    }
    const std::string_view inode = target.substr(
        prefix.size(),
        target.size() - prefix.size() - 1U
    );
    return is_decimal(inode) ? std::string(inode) : std::string{};
}

bool unix_socket_line_is_owned(
    std::string_view line,
    const std::vector<std::string> &owned_socket_inodes
) {
    const std::string inode = unix_socket_inode_from_line(line);
    return !inode.empty() &&
        std::find(owned_socket_inodes.begin(), owned_socket_inodes.end(), inode) !=
            owned_socket_inodes.end();
}

bool scan_owned_unix_socket_artifacts(
    const std::vector<std::string> &owned_socket_inodes,
    const std::vector<std::string_view> &suspicious_needles
) {
    if (owned_socket_inodes.empty()) return false;
    std::ifstream file("/proc/net/unix", std::ios::binary);
    if (!file) return false;

    std::size_t bytes_read = 0;
    std::size_t lines_read = 0;
    std::string line;
    while (std::getline(file, line)) {
        bytes_read += line.size() + 1U;
        if (bytes_read > kMaximumUnixSocketBytes || ++lines_read > kMaximumUnixSocketLines) {
            return false;
        }
        if (unix_socket_line_is_owned(line, owned_socket_inodes) &&
            contains_case_insensitive(line, suspicious_needles)) {
            return true;
        }
    }
    return false;
}

}  // namespace lumen::security
