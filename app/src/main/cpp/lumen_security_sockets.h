#pragma once

#include <cstddef>
#include <string>
#include <string_view>
#include <vector>

namespace lumen::security {

std::string socket_inode_from_fd_target(std::string_view target);

bool unix_socket_line_is_owned(
    std::string_view line,
    const std::vector<std::string> &owned_socket_inodes
);

bool scan_owned_unix_socket_artifacts(
    const std::vector<std::string> &owned_socket_inodes,
    const std::vector<std::string_view> &suspicious_needles
);

}  // namespace lumen::security
