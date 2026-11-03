# 退出 PDF 分屏后的实时笔迹（2026-11-04）

用户反馈：PDF 分屏后取消分屏，原分屏区域的书写不能立即显示，提笔后才刷新。Release 80 的 View 失效调整未充分解决用户设备上的现象；不能将此前 JVM 测试结果视为显示问题已修复。

## 本轮源码调整

`GlWetPad.resize()` 原先销毁并重建 EGL，但仍复用同一个独立 Surface / SurfaceControl，只调整队列尺寸。尺寸正确、EGL 回报 SINGLE，并不能证明目标 GPU 的合成器正在读取新绘制的缓冲。本轮以该复用路径为疑点，Android 12 及以上在尺寸变化时同时重建独立层及 Surface 队列，再执行 EGL 单缓冲初始化。较旧系统继续使用 SurfaceView 管理的 Surface。

销毁 EGL 时推进回调代次，使旧层排队的隐藏、清空和关闭自动刷新操作失效，防止它们作用于新层。续写路径与新笔画路径一样检查 View 和 EGL 的尺寸一致性，不把新尺寸的笔迹送往旧缓冲。

现有笔迹移交仍通过 `onSurfaceLost` 退回普通画布；不修改文档、PDF 原始数据或笔迹简化逻辑。该改动同时涉及分页笔记与无限画布共用的湿墨层，需一并回归。

## 验证边界

JDK 21，使用仓库 Gradle wrapper 8.13、AGP 8.12.3、SDK 36 和本机 Build Tools 36.0.0：

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
ANDROID_HOME=/home/empathless/Android/Sdk \
./gradlew --offline --no-daemon -I tmp/server-build.init.gradle \
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

构建成功，2558 项 JVM 测试通过，失败/错误/跳过均为 0；Android 测试源码编译通过。`tmp/server-build.init.gradle` 为本机忽略文件，仅指定 `android.buildToolsVersion = '36.0.0'`，避免 AGP 默认请求本机未安装的 35.0.0。`git diff --check` 通过。

`adb devices` 没有连接设备，因此未运行 Android 事件测试或 GPU 显示验证。JVM 结果不能验证 Surface 合成；本次尚未确认设备根因或实际修复效果。本轮不执行设备安装，不生成交付 APK，不推送。已检查 AGENTS.md，无长期约定需要变更；README.md 补充本轮状态及记录入口。保留工作区原有的构建/预览说明修改，不纳入本轮提交。

## 待目标设备验收

记录 Wacom MovinkPad Pro 14 的系统构建、Android 版本、GPU、刷新率及 Pro Pen 3 型号。对 PDF 分别执行单页书写、左右分屏、取消左/右窗格后持续书写，确认 MOVE 阶段就能看到笔迹；反复切换并检查原分屏区域和扩展区域。补测上下分屏、同 PDF 共享视图、不同 PDF、分页笔记及无限画布混合分屏、横竖屏和侧栏尺寸变化。

比较前缓冲开/关；检查快写、长笔画、荧光笔、取消事件、撤销、保存重开以及是否有闪烁、双重笔迹、错位或明显帧耗时增加。只有目标设备结果才能确认本轮是否解决用户报告的问题。
