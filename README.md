# aerial-tv-legacy

**给 Android 4.3 老电视的一块干净桌面。**

向 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 致敬。这个项目延续 Aerial 的 tvOS 风格、壁纸和遥控器交互，为无法运行 Compose 的 **Android 4.3 / API 18** 重新实现了 Java / Canvas 版本。

这是独立的兼容项目，并非 Aerial 官方版本。新系统请优先使用原版 Aerial。

[下载 APK](https://github.com/KawaiiHachimi/aerial-tv-legacy/releases/latest) · [原版 Aerial](https://github.com/pengchujin/aerial-tv)

## 界面

![首页](docs/screenshots/home.png)

| 长按整理 | 控制中心 |
| --- | --- |
| ![应用菜单](docs/screenshots/menu.png) | ![控制中心](docs/screenshots/control-center.png?v=1.3.1) |

以上为 Android 4.3 模拟器的原生 1920×1080 屏幕截图，不代表电视真机验证。截图保留了模拟器的应用列表。

## 功能

- 无广告、无推荐流，以已安装应用为中心。
- 遥控器方向键导航、焦点缩放、按压反馈和边缘回弹；尽量减少卡片与文字阴影。
- 应用移动、文件夹、隐藏与恢复、应用信息，以及交由系统确认的卸载。
- 可操作的控制中心：网络和系统设置入口、音量调节、个性化、屏保。
- 15 款离线绘制的壁纸；时钟、名称显示、玻璃底板和焦点样式设置。
- 可配置空闲时间的时钟屏保；唤醒按键不会误启动应用。
- 内置 Google Sans Regular / Medium 静态字体；缺少的汉字使用系统中文字体回退。
- 布局和偏好保存在本地；后台不运行持续动画。

## 兼容与边界

本项目主要面向 Android 4.3 老电视，最低 API 18，不依赖 AndroidX、Compose 或 native 库。发布包保持 DEX 035 和 APK v1 签名。

目前运行验证来自 Android 4.3 x86 模拟器。真实电视、遥控器、MStar GPU、厂商输入源及背光控制需要逐机验证。画面/亮度入口交给系统设置；LeTV 旧版信号源优先使用其显式 Activity 入口。

尚未移植原版的航拍视频、在线天气、手机配置/上传、自动升级及家长密码。此仓库不包含电视固件、厂商 APK、设备密钥或用户数据。

## 安装

从 Releases 下载 `AerialLegacy.apk`，通过电视自带的 APK 安装方式或 ADB 安装：

```bash
adb install -r AerialLegacy.apk
adb shell am start -n app.aerial.legacy/.MainActivity
```

按主页键，在系统允许的情况下选择 Aerial 并设为默认桌面。部分国产电视对 HOME 有自己的限制，不能保证只装 APK 就能接管；不要为此直接停用原厂桌面。

包名：`app.aerial.legacy`。覆盖更新需要相同签名。自己编译的开发包与 Releases 可能使用不同签名。

## 构建

需要 JDK 17+、Python 3、`zip`、Android SDK 的 API18 平台和 build-tools 36.0.0。

```bash
# 使用 Android SDK 的 sdkmanager 安装构建依赖
sdkmanager "platforms;android-18" "build-tools;36.0.0"

export ANDROID_SDK_ROOT=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk
./build.sh
```

输出：`dist/AerialLegacy.apk` 和 `dist/SHA256SUMS`。构建只使用本地工具与内置资源，不在构建时下载 Maven 依赖。

未指定签名配置时，脚本在忽略提交的 `local/` 中生成开发密钥。用于持续发布时，应保留自己的签名密钥，并通过环境变量指定：

- `KEYSTORE_PATH`：密钥文件路径。
- `KEY_ALIAS`：别名，默认 `aerial`。
- `KEYSTORE_PASSWORD`：密码；默认仅适用于本地开发密钥。
- `OUT_DIR`、`BUILD_TOOLS`、`PLATFORM_JAR`：可选输出或工具路径覆盖。

不要把签名文件、密码或设备地址提交到仓库。CI 构建使用临时开发签名，下载正式更新请使用 Releases。

## 测试

```bash
# 无需 Android 设备的布局模型测试
./tests/run.sh

# 构建厂商兼容桥和原生测试 APK
./build.sh --with-bridge
./tests/build-instrumentation.sh

# 只在测试机上安装并运行
adb install -r dist/AerialLegacy.apk
adb install -r dist/T2launcher.apk
adb install -r build/AerialUiTests.apk
adb shell am instrument -w -e class app.aerial.legacy.UiJourneyTest \
  app.aerial.legacy.tests/android.test.InstrumentationTestRunner
```

布局测试覆盖排序、取消移动、文件夹、隐藏恢复、Unicode 名称和卸载清理。原生测试覆盖字体、中文回退、动画收敛、按键操作、持久化和可选 LeTV 桥接接口。测试不是硬件信号切换或真机刷机的替代品。

## 可选 LeTV 桥接

`extras/t2launcher/` 仅供旧 LeTV 固件集成：保留 `com.stv.launcher` 的启动入口、普通启动查询和 `ShowTitleService` Messenger 绑定契约，不注册 HOME。Aerial 会在自己的应用列表中隐藏这个兼容包。

它不实现厂商标题栏、不模拟电视硬件，也不保证修复所有信号源故障。普通电视或盒子不需要安装。此仓库的默认构建仅生成桌面 APK；`--with-bridge` 才额外生成桥接包。

## 致谢与许可

- **Aerial / pengchujin**：产品理念、界面设计、壁纸算法、图标与交互参考。[原项目](https://github.com/pengchujin/aerial-tv)，MIT。
- **Google Sans**：官方 Android 静态字体，SIL Open Font License 1.1；授权随字体内置。[字体项目](https://github.com/googlefonts/googlesans)。
- **Material Symbols Rounded**：部分矢量路径，Apache License 2.0。[图标项目](https://github.com/google/material-design-icons)。

兼容实现采用 MIT，保留上游版权和第三方许可，详见 [LICENSE](LICENSE)、[NOTICE](NOTICE)、各 `LICENSE-*.txt`。
