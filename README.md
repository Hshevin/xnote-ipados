# XNotes for MovinkPad

面向 **Wacom MovinkPad Pro 14（Android 15）和 Pro Pen 3** 的手写笔记应用，基于 XNotes 开发。笔记保存在设备上，无需账号或云同步。

## 下载与安装

从 [GitHub Releases](https://github.com/Hshevin/xnotes-android/releases) 下载本 fork 的 **ARM64 APK**，在平板上打开安装。已安装本 fork 正式版的用户可用同签名的新版本直接覆盖升级。AI 和双窗格独立测试版可以与正式版并存，各自保存数据。

## 主要功能

- **手写笔记**：使用分页笔记或无限画布，混排手写、文字和图片。
- **PDF 批注**：导入 PDF，在页面上手写或标注文字，保留原始 PDF。
- **双窗格**：并排打开笔记、PDF 或无限画布，也可同时查看同一份分页文档的两个位置。
- **Pro Pen 3 快捷键**：在「设置 → 输入 → Pro Pen 3 按键映射」为三个侧键分配工具或撤销、重做。工具可设为按住临时使用，或按一下切换。
- **黑白界面**：支持浅色和夜间模式，以灰阶区分界面层级。
- **可选 AI**：在设置中添加一个或多个模型供应商（各自的地址、API 密钥和模型），在输入栏按供应商切换模型，从主菜单打开聊天，或在文档截图后选择「AI」。主动发送时才会将当前对话及所选截图或 PDF 内容发送给配置的供应商；联网搜索默认关闭。

## PDF 保存与备份

当前源码将 PDF 原件统一保存在应用私有目录，日常笔记只保存引用和批注，相同原件共享一份；旧笔记首次打开时迁移。通过应用分享或导出 `.xnote` 时会重新嵌入 PDF，得到可独立打开的完整笔记包。

**备份或换设备请使用应用的分享／导出功能，不要仅复制日常 `.xnote` 文件。** 清除应用数据或卸载会删除私有 PDF 原件，即使外部文件夹仍有引用式笔记，也不能据此恢复原件。迁移失败时保留原件或恢复资料，并提示处理。

此存储调整已接入源码，尚未打包交付；已发布 APK 的行为以各版本说明为准。详见 [单份 PDF 存储与迁移记录](docs/movinkpad/pdf-single-copy-2026-11-05.md)。

## 更多资料

- [版本更新与验证记录](docs/movinkpad/release-2026-11-02.md) · [早期开发记录](docs/movinkpad/development-history.md)
- [源码构建与测试](docs/movinkpad/server-build.md)（JDK 21） · [MovinkPad 适配与验证范围](MOVINKPAD.md)

本项目源自 [XNotes Android](https://github.com/shardulvs/xnotes-android)，沿用 [MIT 许可证](LICENSE)。
