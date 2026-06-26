#include "utils.h"

#include <io.h>
#include <stdio.h>
#include <windows.h>
#include <shellapi.h>

#include <iostream>

namespace {

std::string Utf8FromUtf16(const wchar_t* wide_string) {
  if (wide_string == nullptr) {
    return std::string();
  }
  int size = WideCharToMultiByte(CP_UTF8, 0, wide_string, -1, nullptr, 0,
                                 nullptr, nullptr);
  if (size == 0) {
    return std::string();
  }
  std::string utf8(size - 1, 0);
  WideCharToMultiByte(CP_UTF8, 0, wide_string, -1, utf8.data(), size, nullptr,
                      nullptr);
  return utf8;
}

}  // namespace

void CreateAndAttachConsole() {
  if (::AllocConsole()) {
    FILE* unused;
    freopen_s(&unused, "CONOUT$", "w", stdout);
    freopen_s(&unused, "CONOUT$", "w", stderr);
    std::ios::sync_with_stdio();
  }
}

std::vector<std::string> GetCommandLineArguments() {
  int argc = 0;
  wchar_t** argv = ::CommandLineToArgvW(::GetCommandLineW(), &argc);
  std::vector<std::string> command_line_arguments;

  if (argv != nullptr) {
    for (int i = 1; i < argc; ++i) {
      command_line_arguments.push_back(Utf8FromUtf16(argv[i]));
    }
    ::LocalFree(argv);
  }

  return command_line_arguments;
}
