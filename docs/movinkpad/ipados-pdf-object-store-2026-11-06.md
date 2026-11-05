# iPadOS 私有 PDF 对象库基础（2026-11-06）

用户要求：持续实现第一版 iPadOS 等价移植，最低 iPadOS 15.6。
状态：对象库基础已实现，macOS 5 项存储测试通过，iPadOS 15.6 类型检查通过；尚未接入应用导入与 PDFKit。
本轮边界：源码、单元测试及专用模拟器验证；无真机安装、签名、Archive、推送或发布。

## 实现

`ios/Sources/LocalStorage/PDFObjectStore.swift` 使用 actor 串行管理对象、索引和活动租约。调用端须为每个私有目录持有一个应用级实例。文件层流式计算 SHA-256，65,536 字节分块复制，不将大型 PDF 整份加载内存。暂存文件同步后同目录 rename 发布；索引也采用暂存、同步、原子替换，成功后才更新内存状态。

按哈希去重并验证已有原件；只有显式 restore 且所选文件哈希等于记录时才可替换损坏原件。来源文件始终保持不变。索引发布失败保留已验证对象；索引缺失但存在原件、版本不支持或损坏时拒绝初始化，不自动建立空索引并清理文件。

引用按 owner 管理；活动租约阻止回收，释放失败保留保护。解除引用或释放租约后重新开始七天宽限期。清理只处理索引中无引用且无活动租约的到期对象，不扫描删除未知对象。进程结束后活动任务不再存在；持久引用继续保护文件。索引失败遗留的对象保留供后续恢复，不自动清理。

当前仅实现原件对象库，不宣称验证 PDF 结构；接收端仍须先完成 PDFKit/包格式校验再发布笔记引用。调用端尚未创建 Application Support 目录装配，也尚无文件协调/security-scoped URL、引用式笔记发布事务、UI 错误翻译或损坏索引恢复入口。

## 验证

`swift test --package-path ios --scratch-path tmp/ipados-swift-build --disable-sandbox`：5 项存储测试通过，原有 3 项输入状态测试仍通过。

- 去重、原始字节与重启后引用恢复。
- 引用与租约期间禁止清理，解除后等待宽限期。
- 错误 PDF 不能重新关联，哈希正确时可修复损坏原件。
- 损坏/缺失索引不被空索引覆盖。
- 强制索引发布失败时保留对象与内存引用。

iPadOS 15.6 目标 Swift 6 类型检查通过。模拟器运行入口为 `sh scripts/ipados/check-ios-storage.sh <UUID>`；该探针只验证文件与租约，不把简化字节样本当作 PDFKit 或真实大型扫描件验收。

尚未验证：磁盘不足、进程强退时点、真实 File Provider、真实大型 PDF 性能、哈希缓存、应用级租约调用完整性、PDFKit 和目标硬件。普通单元测试不能证明这些边界。

iOS 27.2 / iPad Pro M4 模拟器文件探针已执行通过，输出 `PASS: iOS PDF object deduplication, byte preservation, lease and collection`，退出码 0。没有运行真机测试。
