# iPadOS PDFKit 只读探针（2026-11-06）

用户要求：继续实现第一版移植，保持 PDF 原件和 Android 文档坐标语义，最低 iPadOS 15.6。
状态：PDFKit 只读会话及模拟器探针已实现；合成样本验证通过；真实大型 PDF 与真机性能待验证。
本轮边界：普通构建、单元测试和专用 iPad 模拟器；不签名、不 Archive、不真机安装、不推送或发布。

## 实现

`PDFPlatform/PDFReadSession` 从对象库取得租约后打开 PDFDocument，内部对象不向调用端暴露，只提供页数、页面盒、旋转、坐标变换、文字与文字命中、页面预览。关闭前不释放租约，关闭后拒绝访问；无效 PDF 打开失败时释放租约。显式 close 的持久化错误向调用者抛出，析构仅做尽力清理。

CoreGraphics 的 CropBox 绘制变换加 Y 轴反转，建立 PDF 用户空间到左上角页面坐标的映射；测试预期来自 Android PdfPageGeometry 的 0/90/180/270 度合同。编辑器视口的缩放平移仍在此映射之外。

预览使用 PDFKit thumbnail 接口，包含原 PDF 标注。请求尺寸及返回图像限制为最多 4,194,304 像素，不缓存历史预览。此上限不代表 PDFKit 内部解析或纹理分配的内存预算；正式编辑器仍须分块渲染、任务取消和缓存预算。当前 PDFKit 调用串行在 MainActor，不能将其描述为大型文档低延迟实现。

## 验证

- macOS Swift：3 项 PDF 测试覆盖四种旋转与非零 CropBox、坐标正逆变换、预览像素含纸面及内容、上限拒绝、无效页面、关闭访问、原件字节和租约保护；原有 5 项存储及 3 项输入测试一并回归。
- iOS 27.2 / iPad Pro M4 模拟器：`check-ios-pdf.sh` 创建含文本、图形、四种旋转与裁切的合法合成 PDF，实际检查文字提取及词命中、预览、坐标往返、原件字节及关闭回收。输出 `PASS: iOS PDFKit rotations, crop coordinates, text hit, previews, bytes and leases`，退出码 0。
- 模拟器代码按最低 iOS 15.6 编译；不能据此声称在 iPadOS 15.6 真机运行已验证。

合成 PDF 只用于可重复正确性测试，不替代真实扫描件、复杂字体、加密 PDF、链接/原标注导出保真、大文件内存与并发测试。PDFKit 技术选型的完整检查点仍未通过；当前未改用 PDFium，也未删除该备选路线。
