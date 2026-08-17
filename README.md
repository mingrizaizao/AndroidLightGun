# Android Light Gun

一个基于 Android 手机摄像头的自制光枪项目。

本项目使用 Android 手机摄像头识别显示器上的白色目标边框，并将检测到的位置作为光枪瞄准位置输出。项目同时支持通过音频输入检测外部光枪扳机信号，并可通过 Bluetooth HID 将瞄准位置和按键状态发送给电脑。

项目主要用于复古街机游戏、光枪游戏以及自制光枪硬件的实验与研究。

## 使用方法

- 需要在app打开后再连接蓝牙，如果之前手机连过电脑要先删除配对，然后打开app会提示是否允许被发现，然后电脑上添加蓝牙设备，搜到手机后连接会提示配对，这样就行了，以后再打开app会自动连接，不需要配对了~
- 可以通过拖动屏幕上方的拖动条调整检测阈值，屏幕上的白色边框能够稳定检测出红线就可以了~
- 最好在比较暗的环境中使用，更容易检测到屏幕的白色边框~

## Features

- 使用 Android 手机摄像头进行光枪目标检测
- 基于 OpenCV 进行图像处理和白色边框检测
- 支持摄像头画面实时分析
- 支持通过音频输入检测外部扳机信号
- 支持 Bluetooth HID
- 将手机检测到的瞄准位置发送给电脑
- 横屏运行
- Android 原生 + C++/OpenCV 实现
- 项目代码开源，可用于学习、修改和二次开发

## How It Works

基本工作流程：

```text
显示器
  │
  │ 显示游戏画面 / 白色目标边框
  ▼
Android 手机摄像头
  │
  ▼
OpenCV 图像处理
  │
  ├── 灰度化
  ├── 二值化
  ├── 轮廓检测
  └── 目标边框判断
  │
  ▼
计算瞄准位置
  │
  ▼
Bluetooth HID
  │
  ▼
Windows / 游戏
```

扳机部分可以通过手机麦克风采集外部电路产生的音频波形，通过波形变化判断扳机按下和释放状态。

## Project Structure

当前项目主要结构如下：

```text
AndroidLightGun/
├── manifests/
├── java/
│   └── io.github.mingrizaizao.androidlightgun/
│       ├── AudioMonitor.java
│       ├── LightGunActivity.java
│       └── MyJavaCameraView.java
│
├── cpp/
│   ├── includes/
│   ├── CMakeLists.txt
│   └── jni_part.cpp
│
├── res/
│   ├── drawable/
│   ├── layout/
│   │   └── activity_lightgun.xml
│   └── values/
│
├── build.gradle
├── gradle.properties
├── gradle-wrapper.properties
├── settings.gradle
└── LICENSE
```

## Requirements

### Android

- Android Studio
- Android SDK
- Android NDK
- CMake
- Android 手机

当前项目配置：

```text
compileSdkVersion: 34
targetSdkVersion: 34
minSdkVersion: 21
Android Gradle Plugin: 8.6.0
OpenCV: 4.12.0
```

建议使用支持上述 Android Gradle Plugin、SDK、NDK 和 CMake 配置的 Android Studio 环境。

## Build

1. 克隆项目：

```bash
git clone https://github.com/mingrizaizao/AndroidLightGun.git
```

2. 使用 Android Studio 打开项目。

3. 等待 Gradle 同步完成。

4. 确认 Android SDK、NDK 和 CMake 已安装。

5. 连接 Android 手机并启用 USB 调试。

6. 编译并运行项目。

如果你的 GitHub 仓库名称与上面的示例不同，请将 clone 地址替换为实际仓库地址。

## Android Permissions

项目需要使用以下 Android 权限：

- Camera：用于获取摄像头画面
- Record Audio：用于检测扳机音频信号
- Bluetooth：用于蓝牙通信
- Bluetooth Connect / Scan / Advertise：用于 Bluetooth HID 相关功能

首次运行时，请根据 Android 系统提示授予相应权限。

## Version

当前版本：

```text
1.0.0
```

Android versionCode：

```text
1
```

## Third-Party Libraries

本项目使用 [OpenCV](https://opencv.org/) 进行图像处理。

OpenCV 使用 Apache License 2.0 授权。本项目自身代码使用 MIT License 授权。

请注意：

- MIT License 适用于本项目作者编写的代码。
- OpenCV 及其相关第三方组件仍遵循各自适用的许可证。
- 使用、修改或重新分发本项目时，请同时遵守相关第三方组件的许可证要求。

## 配套 Windows Host

如果需要在 Windows 上运行街机光枪游戏，并使用固定的白色边框辅助摄像头进行光枪定位，可以使用配套的 Windows 游戏窗口管理工具：

LightGunHost

LightGunHost 可以将已经运行的游戏窗口调整到固定位置，并在游戏四周提供稳定的纯白色区域，方便 AndroidLightGun 使用摄像头进行白色边框检测。

👉 [查看 LightGunHost 项目](https://github.com/mingrizaizao/LightGunHost)

两个项目可以配合使用：

AndroidLightGun
Android 手机 + 摄像头 + OpenCV
        │
        │ 识别白色边框 / 计算瞄准位置
        ▼
     光枪输入
        │
        ▼
LightGunHost
Windows + 游戏窗口
        │
        └── 固定游戏位置 + 提供白色边框

## License

本项目使用 MIT License。

详见项目根目录中的 [`LICENSE`](LICENSE) 文件。

```text
Copyright (c) 2026 mingrizaizao
```

## Author

**mingrizaizao**

GitHub：

https://github.com/mingrizaizao

## Disclaimer

本项目主要用于个人学习、开源交流、硬件实验和复古游戏研究。

不同 Android 手机、摄像头、显示器以及游戏对输入设备的支持方式可能存在差异，因此实际使用效果可能有所不同。

使用本项目时，请遵守当地法律法规以及相关游戏、硬件和软件的使用条款。

## Roadmap

后续可能继续完善：

- 提高不同环境光照条件下的识别稳定性
- 优化摄像头分析延迟
- 支持更多光枪硬件
- 改进 Bluetooth HID 兼容性
- 增加更多游戏适配
- 完善参数调节和调试功能
- 持续优化项目文档
