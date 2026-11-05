# iPad 模拟器与模型探针（2026-11-06）

用户要求：持续实现完整移植，最低 iPadOS 15.6；暂时没有真机。
状态：iPad 模拟器已启动探针应用；Swift/Kotlin 模型与采样编解码在 iOS 模拟器执行通过；完整应用仍在开发。
本轮边界：源码、普通构建与专用模拟器验证；无真机安装、Archive、签名、推送或发布。

## 模拟器

安装 iOS 27.2 runtime（24B8117），创建 XNotes iPad P0，模拟机型 iPad Pro 11-inch M4（8GB）。Xcode 随后自动开始下载第二个运行时 build 24B91016s，因已有可用运行时而取消；已安装的 24B8117 保留且成功启动。

无签名构建的 `com.xnotes.ipados.probe` 成功安装、启动，中文浅色布局截图已检查。证据存放于忽略的 tmp 目录。本次是探针界面，不是已完成的编辑器。iOS 27.2 模拟器结果不能代表 iPadOS 15.6 实际运行，也不代表 Apple Pencil 或 GPU 真机兼容。

## 模型修改链路

探针直接纳入原几何模块（Pt、Affine、Rect、Geometry、Obb、Flip），StrokeGeometry/Sample 由原源码生成，仅补 `kotlin.concurrent.Volatile` 导入以支持 Native。Android 源码未改变。

采样读入后构造真实 Sample，经 Affine 变换并 copy 修改坐标，重新使用原写入算法编码；压力和时间保持。Swift 验证平移后 JSON 的明确结果、逆平移还原及损坏输入错误边界。在 iOS 模拟器进程执行，输出 PASS，退出码 0。

可重复入口（先执行 check-kmp.sh 生成 framework）：

```sh
sh scripts/ipados/check-ios-interop.sh <已启动模拟器UUID>
```

仍未验证完整 Stroke/CanvasItem、图层、撤销、文档 ZIP 和 PDF；此结果只覆盖代表性模型片段及采样编码。共享模块边界仍须完整格式链路和性能证据后确定，P0 检查点 A 未通过。

英文夜间模式实际截图也已检查：纯黑背景、白色文字与输入区域边框，未见文字裁切。新增模型变换测试后，JVM 10 项与 Native 7 项均通过；macOS Swift 和可重复的 iOS 模拟器脚本均输出 PASS。上述截图不涵盖完整产品 UI 或 Pencil 输入。
