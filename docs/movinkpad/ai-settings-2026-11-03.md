# AI 设置扩展、窗口位置记忆与主页侧栏修复（2026-11-03）

状态：源码已实现。JVM 单元测试全部通过（2555 项，含新增 `AiSettingsOptionsTest` 8 项）；模拟器（API 32 ARM64，2160×1600，200/320 dpi）检查了侧栏、设置界面、模型菜单与浮窗位置。真实供应商请求、思考参数在各家接口上的实际效果、MovinkPad 真机交互尚待验收。按用户要求本轮未打包 APK（只为模拟器检查构建了 Debug 包，未交付）。

## 主页左上角箭头无法收回侧栏（`ui/Editor.kt` `showRail`）

- 原因：设置由两个窗格的 Editor 通过 `LiveSettings` 共享，但侧栏折叠状态 `backstageRail` 是每个 Editor 各自的 Compose 状态。在分屏窗格首页切换过侧栏后，共享设置与主 Editor 的状态不一致；`showRail()` 拿共享值比较并提前返回，主页的「‹」收起和「≡」展开就都没反应。
- 修复：以本 Editor 显示的状态判断，并同步两个窗格的状态后再写入设置。
- 模拟器复现与验证：分屏 → 在窗格首页展开/收起 → 回主页点按钮。修复前按钮无反应，修复后主页跟随窗格状态，且可正常展开、收起。

## AI 设置（`ui/AskAi.kt`、`ai/AiConnection.kt`、`ai/AiProtocol.kt`）

参照 dsh-Go（`ccch1mneyyy/dsh-Go`，main，`ui/providers/*`、`ui/chat/model_sheet.dart`、`ui/settings/personalization_page.dart`）的分组方式：供应商 → 模型 → 思考 → 个性化，模型与思考深度在输入栏用一个胶囊切换。独立实现，未复制代码；外观遵循本项目黑白灰阶、Tokens 字号间距和 48 dp 触控区。

设置改为大尺寸对话框，顶部「✕ / AI 设置 / 保存」，内容分组成描边卡片：

- **供应商**：URL、API Key（不变）。
- **模型**：保存的模型列表，可手动添加、移除、点行设为默认（反色「默认」标记）。「从供应商获取」只在点击时向 `{URL}/models` 发一次 GET（带 API Key），返回列表可筛选并勾选加入。这是设置页唯一的联网动作。
- **思考**：默认思考深度（默认 / 关闭 / 低 / 中 / 高）；参数格式（自动按供应商、OpenAI `reasoning_effort`、OpenRouter `reasoning`、DeepSeek `thinking.type`、通义千问 `enable_thinking`）；最大输出长度 256–131072（原固定 4096）。「默认」不发送任何思考字段，沿用供应商默认。DeepSeek 与千问格式只有开/关。
- **个性化**：自定义提示词，最多 4000 字，作为系统消息放在每次回答请求的最前面；联网检索规则仍附在其后。
- **联网搜索**：DeepSeek 搜索密钥（不变）。

联网检索规划与 PDF 读取步骤使用 `AiConfig.forTools()`：同一模型，但不带思考字段和自定义提示词，避免破坏它们要求的纯 JSON 回复。

### 每个会话的模型与思考

输入栏「＋ 联网」右侧新增胶囊「模型 · 深度 ⌄」。菜单列出已保存模型和思考深度分段，选择只影响本会话之后的问题，正在生成的回答不受影响。选择保存在会话记录中（`model`、`effort` 字段，空/缺省表示跟随默认）；旧记录没有这些字段时照常读取，未知深度值被忽略。

### 存储

加密配置 `ask-ai.config` 新增 `models`、`effort`、`thinkingFormat`、`maxTokens`、`instructions`，缺省时取默认值，旧配置无需迁移；保存仍是原子写入，失败保留旧文件。密钥与提示词都只在 noBackupFilesDir，不进入导出。

截图：[设置（夜间·中文）](ui-2026-11-03/ai-settings-dark-zh.png)、[模型与思考菜单](ui-2026-11-03/ai-model-menu-dark-zh.png)。

## 浮窗位置记忆（`AiSessions.windowOffset`）

浮窗位置从按会话的局部状态移到应用级 `AiSessions`。拖动后，新会话、关闭后再从截图打开的会话都出现在上次的位置；尚未拖动过时仍默认停在右侧工具栏下方。位置只保存在内存中，进程重启后回到默认位置。模拟器已验证新会话与重新打开两种情况。

## 待验证

- 真实供应商：`/models` 返回格式、各家思考字段是否被接受（尤其 OpenAI 兼容服务对 `reasoning_effort: "none"` 的处理）、大输出长度。
- MovinkPad 真机：设置对话框与键盘、胶囊菜单触控、分屏主页侧栏。
