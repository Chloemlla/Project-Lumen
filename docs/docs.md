引入 **Shizuku** 后，安卓端护眼软件的实现将迎来**质的飞跃**。
传统的“悬浮窗遮罩”方案只是在屏幕上贴了一层半透明黄膜，不仅**无法真正减少蓝光发射**（屏幕背光里的蓝光依然在穿透黄膜），还会导致屏幕发灰、对比度雪崩，且截图会变黄。
利用 **Shizuku** 获得 ADB 级别的高级系统权限后，我们能够直接调用 Android 系统的**色彩矩阵（Color Matrix）**和**系统无障碍滤镜（Accessibility Display Adjustment）**。这类似于 PC 端修改硬件 Gamma 查找表，从而在系统底层真正实现亮度和蓝光的独立调节。
## 一、 基于 Shizuku 的原生防蓝光软件需求（PRD）
### 1. 核心功能需求
 * **硬件级无损独立色温（防蓝光）：** 拖动色温滑块时，直接修改底层色彩转换矩阵（Color Transform Matrix），直接削减硬件发光中的物理蓝光成分。屏幕变黄的同时，画面依旧清晰，**且系统截图、录屏完全不受影响，色彩依然纯净**。
 * **物理背光级独立亮度（突破下限）：**
   * 正常范围内，通过 Shizuku 直接改写系统亮度配置项。
   * 关灯后，一键开启“夜间夜视仪”模式，结合 Shizuku 的系统级 Extra Dim（格外暗）接口，在不伤眼的前提下让背光降到硬件物理极限以下。
 * **自动化无感过渡：** 设定好时间后，系统在后台悄悄更改矩阵参数，无需拉起悬浮窗或任何前台动画，5秒内平滑过渡。
### 2. 非功能需求
 * **免 Root、无悬浮窗：** 仅依赖 Shizuku 授权，不需要用户开启臃肿且容易被杀的“悬浮窗”和“无障碍服务”。
 * **极致保活与极低功耗：** 由于调用的是系统原生接口，App 本身不需要实时绘制画面。参数修改后，App 可以完全转入休眠，耗电量为 **0%**。
## 二、 Shizuku 原生应用的具体技术实现要求
在原生安卓开发（Kotlin）中，调用 Shizuku 执行底层接口主要通过两种方式：直接反射/绑定系统服务（如 IWallpaperManager、IDisplayManager），或者执行高效的 settings 命令行。
### 1. 技术选型
 * **开发框架：** Kotlin + Shizuku API 核心库。
 * **运行机制：** 软件不需要常驻前台服务，只需通过 Shizuku Binder 临时向系统服务注入参数。
### 2. 底层技术实现要求（核心关键点）
#### A. 色温（蓝光）控制：借助色彩矩阵（Color Matrix）
Android 系统在底层（SurfaceFlinger 合成层）拥有一个处理屏幕颜色调整的隐藏服务。使用 Shizuku，我们可以向系统注入一个排除蓝光的 **4 \times 4 色彩矩阵**。
 * **实现原理：** 削减 RGB 中的 B（Blue）通道。当用户调整色温滑块时，软件计算对应的 RGB 系数（例如 R=1.0, G=0.85, B=0.4），然后通过 Shizuku 代理执行系统隐藏的 accessibility 命令或直接调用 IDisplayManager：
   ```bash
   # 示例：通过 Shizuku 运行 shell 命令直接修改系统色彩矩阵（以实现纯硬件级滤镜）
   settings put secure accessibility_display_magnification_navbar_enabled 0
   # 在某些 AOSP 变体中，可以通过改变系统色彩校正配置（Color Correction）来注入自定义黄色矩阵
   
   ```
 * **高级首选（如果目标设备支持）：** 调用 Android 原生的 ColorDisplayManager（夜间模式底层）。通过 Shizuku 提升权限后，直接调用隐藏的 setNightDisplayColorTemperature(int temperature) 接口，直接使用系统最高效的硬件级去蓝光算法。
#### B. 亮度控制：硬件背光 + Extra Dim 双轨制
 * **10% - 100% 亮度：**
   通过 Shizuku 代理写入系统 system 表。
   ```kotlin
   // 通过 Shizuku 运行 ADB 级别的 settings 写入
   // 独立于色温，直接修改屏幕物理背光
   "settings put system screen_brightness $brightnessValue"
   
   ```
 * **0% - 10%（超低亮度）：**
   调用 Android 12 及以上原生引入的 **Extra Dim（格外暗）** 系统核心接口。通过 Shizuku 动态改变系统内置的超暗滤镜强度，从而在硬件背光极低的情况下，再进行一层无损的像素亮度衰减。
   ```bash
   # 调整系统 Extra Dim 的暗度百分比（0-100）
   settings put secure accessibility_display_inversion_enabled 0 
   # 注：具体键值通常为 secure 表中的 accessibility_intensity 或厂商特有键
   
   ```
### 3. 兼容性与边界处理指标

| 场景 / 维度 | 传统悬浮窗方案 | Shizuku 原生方案（实现要求） |
| :--- | :--- | :--- |
| **系统截图/录屏** | 截图是一团焦黄，发给别人看不清。 | **截图完全正常（原色）**。因为色彩矩阵只作用于最终的屏幕物理输出，不污染系统缓冲区。 |
| **UAC与系统弹窗** | 遇到输入密码、安装输入法时，遮罩层往往被系统强行置顶失效或引发安全警告。 | **全场景完美覆盖**。由于是系统级滤镜，无论是锁屏、指纹解锁、安全密码界面，全部能维持舒适的偏黄低亮状态。 |
| **多窗与游戏兼容** | 全屏游戏或看视频时，悬浮窗容易掉帧或被系统杀掉。 | **零性能损耗**。SurfaceFlinger 硬件合成，不占用 CPU，游戏完全不掉帧。 |
| **断电/重启恢复** | 手机重启后必须手动重新打开 App 才能加载遮罩。 | **开机自加载**。通过注册 BOOT_COMPLETED 广播，在开机瞬间通过 Shizuku 自动恢复上一次的色温配置，用户毫无感知。 |

> “我们需要开发一款利用 **Shizuku** 提权的安卓原生护眼软件。拒绝使用悬浮窗（TYPE_APPLICATION_OVERLAY）方案。**必须通过 Shizuku 获得 ADB 权限，色温调节通过修改系统底层色彩转换矩阵（或反射调用 ColorDisplayManager）来实现；亮度调节通过直接改写 settings 亮度值，并在极低亮度下联动激活系统的 Extra Dim（格外暗）特性**，从而实现彻底不污染截图、全场景覆盖且绝对不占内存和额外电量的原生双轴护眼效果。”
>
