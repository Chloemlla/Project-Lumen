#ifndef RUNNER_WIN32_WINDOW_H_
#define RUNNER_WIN32_WINDOW_H_

#include <windows.h>

#include <string>

class Win32Window {
 public:
  struct Point {
    unsigned int x;
    unsigned int y;
    Point(unsigned int x, unsigned int y) : x(x), y(y) {}
  };

  struct Size {
    unsigned int width;
    unsigned int height;
    Size(unsigned int width, unsigned int height)
        : width(width), height(height) {}
  };

  Win32Window();
  virtual ~Win32Window();

  bool Create(const std::wstring& title, const Point& origin,
              const Size& size);
  bool Show();

  void SetChildContent(HWND content);
  HWND GetHandle();
  void SetQuitOnClose(bool quit_on_close);

 protected:
  virtual LRESULT MessageHandler(HWND window, UINT const message,
                                 WPARAM const wparam,
                                 LPARAM const lparam) noexcept;

 private:
  static void RegisterWindowClass();
  static void UnregisterWindowClass();
  static LRESULT CALLBACK WndProc(HWND const window, UINT const message,
                                  WPARAM const wparam,
                                  LPARAM const lparam) noexcept;

  static int window_class_registrations_;
  static constexpr wchar_t kWindowClassName[] =
      L"PROJECT_LUMEN_FLUTTER_WINDOW";

  HWND window_handle_ = nullptr;
  HWND child_content_ = nullptr;
  bool quit_on_close_ = false;

  Win32Window(const Win32Window&) = delete;
  Win32Window& operator=(const Win32Window&) = delete;
};

#endif  // RUNNER_WIN32_WINDOW_H_
