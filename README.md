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
- **可选 AI**：在设置中配置供应商、模型和 API 密钥，从主菜单打开聊天，或在文档截图后选择「AI」。主动发送时才会将当前对话及所选截图或 PDF 内容发送给配置的供应商；联网搜索默认关闭。

## 开发分支

`drawing-layers` 新增分页笔记、PDF 批注和无限画布的图层面板：新增、命名、显隐、锁定、排序、删除及移动选中内容，支持撤销恢复。旧笔记归入默认层；PDF 原文保持独立。此功能尚未打包，真机显示与性能验收待完成，见[图层开发与验证记录](docs/movinkpad/layers-2026-11-05.md)。

## 更多资料

- [版本更新与验证记录](docs/movinkpad/release-2026-11-02.md) · [早期开发记录](docs/movinkpad/development-history.md)
- [源码构建与测试](docs/movinkpad/server-build.md)（JDK 21） · [MovinkPad 适配与验证范围](MOVINKPAD.md)

本项目源自 [XNotes Android](https://github.com/shardulvs/xnotes-android)，沿用 [MIT 许可证](LICENSE)。
