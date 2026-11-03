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

## Release 85 后的用户反馈与继续调查

Release 85 交付后，用户确认上述队列重建调整未解决问题，并补充更小的复现路径：打开 PDF → 打开分屏文件选择首页 → 不打开任何文件就退出分屏 → 笔迹进入原空窗格区域时不能立即显示，提笔后才刷新。用户要求先确定根因再修复。本轮曾准备的整层 SurfaceView 重建及配套测试已撤回；没有提交这些行为改动，也没有据此构建新交付 APK。

用户对照结果：

- 勾选「禁用前缓冲绘制」后正常。
- 随后重新开启前缓冲并再次走空分屏流程，用户确认从第一笔起也全部正常。这是一次恢复正常的观察，不能证明永久修复，也不能解释为「第一笔延迟、后续正常」。
- 无法将目标设备接入当前开发机的 ADB；本机 `adb devices` 无设备，亦未安装 Android 模拟器或系统镜像。本轮没有取得故障时的 GPU/合成器状态。

### 源码中已确认的流程

1. `MainActivity.onSplitDocument` 创建第二 Editor 并将其加入 `paneHomes`，不打开第二份文档。`SplitHost` 根据 `homes` 判断两侧可见，因此即便 `Editor.inSplit`（要求两个文档均已打开）为 false，实际也已经执行分屏布局。这条路径不经过第二份 PDF 的打开/共享。
2. `PaneToolbarAround` 在单文档时调用 `ToolbarAround`，分屏时切换到另一 Compose 分支；分页内容中的 `AndroidView.factory` 使用 `detached(editor.surfaces)`，明确把同一个 FrameLayout 从旧父节点移到新父节点。因此需要调查的不仅是 EGL 尺寸变化，还包括原 SurfaceView 的拆挂与系统绘制节点生命周期。
3. `GlWetPad.surfaceDestroyed` 排队销毁 EGL，清空 handler/thread 引用，`quitSafely()` 后只 `join(500)`，无论线程是否仍存活都调用 `closeLayer()`。下一次 `surfaceCreated` 会在同一对象上启动新线程，EGL、layer、target 等字段仍属于该对象。若旧线程未在 500 ms 内退出，代码没有提供阻止跨代资源访问的保证。这个风险可由控制流确认；目前没有故障设备日志证明它实际触发，更不能直接把它认定为半屏现象的根因。
4. `InteractionController.drawOverlay` 在 `frontInk.live == true` 时跳过普通画布的实时笔画。因此前缓冲接管成功但显示不完整时，普通窗口不会在 MOVE 阶段补画；提笔后正常提交路径仍可能显示完整笔画。这说明现象可以由显示移交产生，但不能区分未绘制、应用裁剪和系统合成裁剪。

### 尚未证实

- 故障时 `FrontInk` 的页面裁剪是否仍为半屏；或裁剪已为全屏、却受系统 SurfaceControl 父层裁剪影响。
- 故障时 EGL 宽高、实际缓冲内容、窗口/Surface 父层裁剪是否一致。
- 旧线程是否跨过 500 ms 等待界限，或旧的布局/渲染事务是否迟到并覆盖新状态。
- 前缓冲开关恢复正常究竟来自哪一级状态重建；不能据此判定重建整个 View 就是正确修复。

交叉核对了 [Android 15 SurfaceView 源码](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/core/java/android/view/SurfaceView.java)：父层几何与裁剪由框架和 RenderThread 管理，并与窗口绘制事务合并；[HandlerThread 源码](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/core/java/android/os/HandlerThread.java)中 `quitSafely()` 只发出退出请求。另有 [AndroidX 的 Compose/SurfaceView 裁剪问题讨论](https://github.com/androidx/media/issues/2158)，但平台版本与设备情况不同，不能作为本设备已命中同一系统缺陷的证据。

本阶段结论：问题已缩小到前缓冲启用时的显示/生命周期路径，具体根因尚未确认。保留 Release 85 源码行为，不继续套用重建方案；后续需要能区分上述原因的证据或可控复现。未运行新的通过性测试；中止了撤回方案的构建，不把它计为验证结果。
