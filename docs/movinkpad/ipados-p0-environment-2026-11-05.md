# iPadOS P0 工具链与原生输入（2026-11-05）

用户要求：持续实现第一版等价移植；最低 iPadOS 15.6，暂时没有验收真机。
状态：原生输入探针源码已实现；共享工具链正在验证；P0 尚未通过。
本轮边界：允许源码和普通构建验证；不签名、不 Archive、不安装、不发布、不推送。

## 环境与范围

Xcode 27.2、Swift 6.4.2、iOS/Simulator SDK 27.2 可用。模拟器运行时列表为空，需要安装。最低部署版本固定为 15.6；挤压/笔杆旋转等后续 API 必须运行时检查，旧系统不伪造能力。暂时没有 iPad 或 Pencil，不填写虚构性能阈值或设备结果。

`ios/Canvas/PencilCaptureView.swift` 建立 UIKit 采集入口：精确位置、原始压力/最大压力、倾角、方位、时间、估计属性与更新身份；正式与预测点分流；触摸身份隔离；取消与宿主移除清理。当前仅采集 Pencil，手指导航由后续编辑器适配。尚未连接正式模型、Metal、系统工具偏好或保存；原生回调正确性仍须设备验证。

## KMP 探针

`probes/kmp` 是独立 Gradle 工程，不改变 Android 模块。使用 Kotlin 2.2.21，直接包含现有 `Pt.kt`，提供批次距离计算边界和 JVM 测试，输出 iOS 真机/模拟器 framework。它只验证几何共享的第一步，不能替代模型、编解码、Swift 调用和格式往返检查点。

```sh
./gradlew -p probes/kmp --project-cache-dir ../../tmp/kmp-probe-cache jvmTest linkDebugFrameworkIosSimulatorArm64 --no-daemon
ruby scripts/ipados/audit-portability.rb > tmp/ipados-portability.json
```

只读依赖审计扫描 core/format 的 209 个 Kotlin 文件，28 个含列出的平台依赖。结果不等于其余文件均可移植：传递依赖和语义差异仍需验证。脚本兼容本机 Ruby 2.6，不依赖第三方包。

已执行 iOS SDK 类型检查：`swiftc -typecheck -target arm64-apple-ios15.6`，通过。该检查没有启动应用或实际触发 UIKit 输入事件。iOS 27.2 ARM64 模拟器运行时下载已启动，完成状态待核实；较新模拟器不能替代 15.6 运行时兼容验收。

## 2026-11-06 编译结果

KMP 独立工程 `jvmTest linkDebugFrameworkIosSimulatorArm64` 成功：1 项 JVM 测试通过，并从原 Android Pt 源码构建了 Apple 模拟器 framework。Swift 调用样本位于 `probes/kmp/swift/GeometryInterop.swift`，独立验证生成接口；尚未执行模拟器运行时调用，也尚无模型/格式往返证据。

新增 `ios/XNotes.xcodeproj`、共享 scheme、SwiftUI 输入探针入口与英/中文资源。最低部署版本 15.6，无签名模拟器 Debug 构建通过。界面只展示输入计数，不是完成的笔记编辑器；不保存 PKDrawing 或引入新文档格式。估计值更新按 estimationUpdateIndex 路由，避免依赖抬笔后的 UITouch 对象身份。取消和退出清除映射。

可重复验证入口为 `sh scripts/ipados/check.sh`；CI 骨架已添加但尚未在 GitHub 执行。Xcode 工程由 `ruby scripts/ipados/generate-project.rb` 确定性生成，新增 App/Canvas 源文件后需重新生成。
