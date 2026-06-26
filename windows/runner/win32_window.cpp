#include "win32_window.h"

#include <dwmapi.h>

int Win32Window::window_class_registrations_ = 0;

Win32Window::Win32Window() {
  RegisterWindowClass();
}

Win32Window::~Win32Window() {
  if (window_handle_ != nullptr) {
    DestroyWindow(window_handle_);
  }
  UnregisterWindowClass();
}

bool Win32Window::Create(const std::wstring& title, const Point& origin,
                         const Size& size) {
  RECT frame = {static_cast<LONG>(origin.x), static_cast<LONG>(origin.y),
                static_cast<LONG>(origin.x + size.width),
                static_cast<LONG>(origin.y + size.height)};

  AdjustWindowRect(&frame, WS_OVERLAPPEDWINDOW, FALSE);

  window_handle_ = CreateWindow(kWindowClassName, title.c_str(),
                                WS_OVERLAPPEDWINDOW, frame.left, frame.top,
                                frame.right - frame.left,
                                frame.bottom - frame.top, nullptr, nullptr,
                                GetModuleHandle(nullptr), this);

  if (window_handle_ == nullptr) {
    return false;
  }

  BOOL dark_mode = TRUE;
  DwmSetWindowAttribute(window_handle_, 20, &dark_mode, sizeof(dark_mode));

  return Show();
}

bool Win32Window::Show() {
  return ShowWindow(window_handle_, SW_SHOWNORMAL);
}

void Win32Window::SetChildContent(HWND content) {
  child_content_ = content;
  SetParent(content, window_handle_);

  RECT frame;
  GetClientRect(window_handle_, &frame);
  MoveWindow(content, frame.left, frame.top, frame.right - frame.left,
             frame.bottom - frame.top, TRUE);
}

HWND Win32Window::GetHandle() {
  return window_handle_;
}

void Win32Window::SetQuitOnClose(bool quit_on_close) {
  quit_on_close_ = quit_on_close;
}

LRESULT Win32Window::MessageHandler(HWND window, UINT const message,
                                    WPARAM const wparam,
                                    LPARAM const lparam) noexcept {
  switch (message) {
    case WM_SIZE:
      if (child_content_ != nullptr) {
        RECT frame;
        GetClientRect(window, &frame);
        MoveWindow(child_content_, frame.left, frame.top,
                   frame.right - frame.left, frame.bottom - frame.top, TRUE);
      }
      return 0;

    case WM_DESTROY:
      window_handle_ = nullptr;
      if (quit_on_close_) {
        PostQuitMessage(0);
      }
      return 0;
  }

  return DefWindowProc(window, message, wparam, lparam);
}

void Win32Window::RegisterWindowClass() {
  if (window_class_registrations_++ > 0) {
    return;
  }

  WNDCLASS window_class = {};
  window_class.hCursor = LoadCursor(nullptr, IDC_ARROW);
  window_class.lpszClassName = kWindowClassName;
  window_class.style = CS_HREDRAW | CS_VREDRAW;
  window_class.cbClsExtra = 0;
  window_class.cbWndExtra = 0;
  window_class.hInstance = GetModuleHandle(nullptr);
  window_class.lpfnWndProc = Win32Window::WndProc;

  RegisterClass(&window_class);
}

void Win32Window::UnregisterWindowClass() {
  if (--window_class_registrations_ > 0) {
    return;
  }

  UnregisterClass(kWindowClassName, nullptr);
}

LRESULT CALLBACK Win32Window::WndProc(HWND const window, UINT const message,
                                      WPARAM const wparam,
                                      LPARAM const lparam) noexcept {
  if (message == WM_NCCREATE) {
    auto window_struct =
        reinterpret_cast<CREATESTRUCT*>(lparam)->lpCreateParams;
    SetWindowLongPtr(window, GWLP_USERDATA,
                     reinterpret_cast<LONG_PTR>(window_struct));
  } else if (Win32Window* window_struct = reinterpret_cast<Win32Window*>(
                 GetWindowLongPtr(window, GWLP_USERDATA))) {
    return window_struct->MessageHandler(window, message, wparam, lparam);
  }

  return DefWindowProc(window, message, wparam, lparam);
}
