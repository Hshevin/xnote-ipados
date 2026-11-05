# iPadOS 格式与 Swift 互操作探针（2026-11-06）

用户要求：继续实现完整 iPadOS 第一版，最低 iPadOS 15.6；暂时没有真机。
状态：采样编解码探针 JVM / macOS Native 测试通过，iOS 模拟器 framework 编译通过；完整格式往返尚未实现。
本轮边界：源码与普通构建验证，不签名、不 Archive、不真机安装、不推送或发布。

## 探针实现

直接使用现有 Pt 源码；JsonPull、JsonWrite、RawSamples 由 `scripts/ipados/prepare-format-probe.rb` 从当前 Android 源文件生成临时副本，位于忽略的 tmp 目录。正式 Android 源码未改动。适配只存在于探针，不能把它作为已完成的共享层抽取。

替代项：Reader/Writer 的内存适配、Java Math.round 的同语义实现、BigDecimal 生成的十进制幂改为双精度字面量、控制字符十六进制转义、StringBuilder.append 的 Kotlin/Native appendRange 接口。后续正式抽取必须保留流式 I/O，不能用这个内存探针承担大型文档读取。

发现：原 JsonPull 在根结束后直接报告 END_DOCUMENT，尾随 JSON 不被拒绝。探针额外提供 requireDocumentEnd 检查并在样本入口调用；Android 行为未修改。此差异需在正式格式兼容与恢复策略中处理，不静默扩大迁移范围。

Swift 调用样本通过 @Throws 接收解析错误，避免 Kotlin 异常越过未声明的 Swift 边界导致进程终止。

## 验证与边界

入口：`sh scripts/ipados/check-kmp.sh`。

- JVM 9 项测试通过，其中 10,000 组采样与直接使用 Java I/O 的原 Android 写入算法逐字节比较，另含数字位模式及舍入边界对照。
- macOS ARM64 Native 6 项测试通过：几何批次、采样往返、损坏输入、字符串、舍入、数组扩容。
- iOS ARM64 模拟器 framework 编译通过。
- 本轮样本是采样 JSON，尚非完整 `.xnote` ZIP，不能据此声称 Android/iPad 文档双向兼容。
- 未覆盖完整对象模型、UUID、图层、PDF、未知 ZIP 条目、冻结保存、Swift 批次性能及 iOS 运行时；黄金包与两轮往返仍待完成。

Swift/macOS 实际执行输出 `PASS: Swift/Kotlin geometry, sample codec, and error boundary`；iOS 15.6 模拟器目标 Swift 类型检查通过。这是宿主运行和 iOS 编译证据，不等于 iPad 运行时或真机验收。
