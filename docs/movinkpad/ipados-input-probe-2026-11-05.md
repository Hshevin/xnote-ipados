# iPadOS 输入探针起步（2026-11-05）

用户要求：按既有 iPadOS 规划实施完整移植。
状态：已实现 P0 输入状态探针基础；macOS Swift 单元测试 3 项通过；完整移植未完成。
本轮边界：普通源码与测试；不打包 APK，不安装、不推送、不签名、不 Archive、不发布。

新增 `ios/Package.swift` 和 InputProbe。该包是探针，不是正式文档模型，也不定义新的笔记格式。每个 session 只接收所属输入身份，预测样本独立保存；提交快照使用值语义，迟到修正生成新 revision；取消、删除、撤销或关闭时调用 invalidate，阻止迟到回调复活数据。

验证环境：Xcode 27.2 / Swift 6.4.2 / macOS ARM64。执行：

```sh
swift test --package-path ios --scratch-path tmp/ipados-swift-build --disable-sandbox
```

3 项 XCTest 全部通过：预测替换和身份隔离；迟到修正和冻结快照；取消清理。初次运行被工具链缓存目录的沙箱权限阻止，获准后重新运行通过。

未验证：UIKit 实际事件、移动/换层与撤销集成、保存失败、KMP/iOS 调用、格式往返、PDFKit、Metal、模拟器和真机。尚未创建 Xcode 应用工程，不能安装使用；P0 检查点 A 尚未通过，P1–P8 均未完成。最低 iPadOS、性能门槛和设备矩阵仍待探针证据确定。

源码初查：BundleIdentity 依赖 Java IO、ZIP、MessageDigest、UUID 与 org.json；格式层另有 DOM/XML、日期和 IdentityHashMap。不能直接把这些源文件放入 commonMain。后续需完成参考审计、黄金文件集、代表性 KMP 模型/编解码/Swift 调用链路和输入/PDF 原生探针，再确定共享边界。不得把本输入探针视为共享层抽取已完成。
