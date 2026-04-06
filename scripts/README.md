# Icon Generation Script

## 使用方法

### 前置要求

1. 在项目根目录放置 `icon.png` 文件（建议尺寸：512x512px 或更大）
2. 安装 ImageMagick：
   - 下载地址：https://imagemagick.org/script/download.php
   - Windows 安装时勾选 "Add to PATH"

### 运行脚本

```cmd
cd scripts
generate-icons.cmd
```

或从项目根目录：

```cmd
scripts\generate-icons.cmd
```

## 生成的图标

脚本会自动生成以下尺寸的 Android 启动图标：

- `mipmap-mdpi/ic_launcher.png` - 48x48px
- `mipmap-hdpi/ic_launcher.png` - 72x72px
- `mipmap-xhdpi/ic_launcher.png` - 96x96px
- `mipmap-xxhdpi/ic_launcher.png` - 144x144px
- `mipmap-xxxhdpi/ic_launcher.png` - 192x192px

## 注意事项

- 脚本会自动删除旧的 XML 矢量图标文件以避免资源冲突
- 确保 `icon.png` 是正方形且分辨率足够高
- 生成后可以直接运行 `flutter build apk` 构建应用
