#include "flutter_window.h"

#include <flutter/generated_plugin_registrant.h>

FlutterWindow::FlutterWindow(const flutter::DartProject& project)
    : project_(project) {}

FlutterWindow::~FlutterWindow() {}

LRESULT FlutterWindow::MessageHandler(HWND window, UINT const message,
                                      WPARAM const wparam,
                                      LPARAM const lparam) noexcept {
  if (message == WM_CREATE) {
    RECT frame = {};
    GetClientRect(window, &frame);

    flutter_controller_ = std::make_unique<flutter::FlutterViewController>(
        frame.right - frame.left, frame.bottom - frame.top, project_);
    if (!flutter_controller_->engine() || !flutter_controller_->view()) {
      return -1;
    }
    RegisterPlugins(flutter_controller_->engine());
    SetChildContent(flutter_controller_->view()->GetNativeWindow());
    return 0;
  }

  if (message == WM_DESTROY) {
    flutter_controller_ = nullptr;
  }

  return Win32Window::MessageHandler(window, message, wparam, lparam);
}
