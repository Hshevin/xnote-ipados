# XNotes MovinkPad fork

This fork targets Wacom MovinkPad Pro 14 and Pro Pen 3. Three-button mappings and a black/white UI palette are implemented in the preview; target-device acceptance is pending. The upstream features below are not a claim of verified MovinkPad compatibility.

- [Preview scope and existing validation](MOVINKPAD.md)
- [Project brief](docs/movinkpad/project-brief.md)
- [Linux build and repository structure](docs/movinkpad/server-build.md)
- [Workspace switching and consistent pen settings](docs/movinkpad/workspace-switch-2026-10-31.md)
- [Branch split and upstream sync](docs/movinkpad/github-sync-2026-11-02.md)
- [Black/white hierarchy, type and spacing tokens](docs/movinkpad/ui-monochrome-tokens-2026-11-03.md)
- [Planned PDF and cursor work](tasks/plan.md)

## AI 第一版（2026-11-03）

源码已合入主工作区，保留 Release 81 的灰阶、字号/间距 tokens 和工具栏布局。尚未开始下一轮 UI 调整；用户随后授权导出 AI preview3 测试 APK，包含本轮合并后的源码。

由独立工作树 `ask-ai` 合入本地主分支的功能包括 PDF、分页笔记及无限画布截图菜单的「AI」。设置中填写 HTTPS 供应商地址、支持图片的模型和 API Key；支持应用内拖动/收起浮窗、流式回答、追问、停止和文本选择复制。当前源码已加入左侧 AI 历史：本地保存、搜索、继续、重命名、删除及 8 秒撤销。关闭浮窗、切换文档或新会话不取消生成；主动停止或确认删除才取消。进程结束后恢复最近保存的内容，并标记中断。

本分支新增可选网络功能，上游下方“no internet access”不适用于此分支。笔记仍离线保存，只有主动发送才上传选区及当前会话。已导出 [AI 独立测试 APK（preview3）](build/ai-preview/XNotes-0.9.7-ai-preview3-arm64.apk)，包名 `com.xnotes.movinkpad.aipreview`，可与 Release 并存。使用 `-PaiPreview=true :app:assembleDebug` 构建；真实接口、密钥存储故障恢复及平板交互尚待验收。详见 [实现与验证](docs/movinkpad/ask-ai-2026-11-03.md)。

当前源码新增默认关闭的「联网搜索 · DeepSeek」开关：设置独立搜索密钥，开启后自动结合截图、当前问题和对话识别检索对象，再搜索和回答，显示来源；无需填写搜索词。此前 **preview2 / versionCode 2** 已包含搜索开关、null 修复、AI 命名和历史会话功能，可覆盖旧 AI 测试版并保留其设置，不覆盖正式 Release。详见实现记录。

另已修复流式回答前出现连续 `null` 的解析问题：仅追加字符串类型的正文。修复已包含在 preview2 APK。

最新源码将主菜单 AI 改为完整聊天页；文档内保留浮窗，退出来源文档时自动关闭，生成继续保存。支持可选「附加当前 PDF」、选择/粘贴本机 PDF；附件保存本地快照，模型按需读取文字、搜索页码范围或查看单页图片，读取结果参与联网检索。当前单文件上限 200 MB，不再限制 30 页；真实模型与大 PDF 性能待验收。

聊天交互源码已完善：宽屏历史与聊天并排，窄屏切换；各会话草稿文字、截图、PDF 引用和联网开关本地恢复。附件准备跨页面继续，支持失败重试/取消；显示读页、检索及回答阶段，过程与来源可折叠。生成时可写下一条草稿，阅读旧消息不强制滚底；已包含在 preview3，界面与目标设备验证待完成。

最新源码修复截图论文搜索跑偏路径：自动识图生成检索词，参考资料与原问题分离。此修复已包含在 preview3，preview2 仍是旧搜索流程。

## 文档登记兼容层（开发中）

源码已接入独立 ZIP 文档 UUID、保留旧负载的迁移与恢复、SQLite 位置/资源登记、共享 PDF 对象，以及按 ID 恢复分屏和失联占位。AI 历史、草稿、撤销和生成任务参与资源引用/租约保护。便携笔记包仍嵌入原 PDF；真实 SQLite、SAF 故障恢复和平板交互待设备验收，已包含在 preview3。见[实施与验证记录](docs/movinkpad/document-registry-2026-11-03.md)。旧笔画保真要求仅针对旧格式导入/升级，日常笔画处理逻辑保持不变。

## 当前开发状态（2026-11-03）

- 本地 `master` 已快进合入 `codex/split-workspace`，包含分屏功能及上游 0.9.7；原分屏分支保留，尚未推送此次合并。见上面的分支同步记录。
- 分屏功能基线为 **0.9.7-split-preview10 / versionCode 77**：支持笔记、PDF、画布分屏、应用内分屏文件管理、默认新分屏位置和重复保存去重。详见 [分屏开发与验收记录](docs/movinkpad/split-preview-2026-11-02.md)。
- 测试包名 `com.xnotes.movinkpad.splitpreview`，与正式包名 `com.xnotes.movinkpad` 分开安装。用户已确认该版本满足当前需求，并要求打包原应用覆盖升级版；当前维护入口为 `master`。
- 本轮验证：2462 项 JVM 测试、39 项 Android 模拟器测试通过。测试数量是本次记录，不代表后续版本已验证。

[下载 Release 82 ARM64 APK](build/xnotes/XNotes-0.9.7-movinkpad-release82-arm64.apk)。

当前覆盖升级版为 **0.9.7-movinkpad / versionCode 82**，使用原应用身份和历史签名。交付与验证见 [Release 记录](docs/movinkpad/release-2026-11-02.md)。测试版的数据不会自动迁入原应用。版本 79 修复返回主菜单后打开普通文件仍保留分屏的问题，普通文件进入单文档，显式恢复分屏不受影响。

构建覆盖升级 Release（须保留本机原签名密钥）：

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew --no-daemon -PmovinkpadPreviewSigning=true :app:testReleaseUnitTest :app:assembleRelease
```

构建独立分屏测试版（JDK 21）：

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew --no-daemon -PsplitPreview=true :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Release 80 包含实时笔迹刷新调整、分屏关闭先回到窗格首页再退出，以及文件树叶子文件夹箭头修复。前缓冲显示和性能仍待目标设备验证；发布结果见 Release 记录。

Release 81（2026-11-03，仅本地打包）加入黑白灰阶层级、统一字号和间距规范，以及 48 dp 触控区。已知在 1280 dp 宽的分屏下工具栏会溢出到滚动区；真机验收待执行。

当前未发布的工具栏调整统一按文档能力分配：插入空白在笔记/PDF 工作区置顶；PDF 文本标注在全 PDF 工作区置顶；混合不支持的文档时下放对应文档栏。缩放/导航/视图等独立设置保持在文档栏。新按钮必须显式分类。本轮随 Release 82 交付。

每个功能阶段或交付前检查本文件与 [AGENTS.md](AGENTS.md)，同步状态、构建入口及维护约定；详细历史和证据保存在 `docs/movinkpad/`。

Build this fork with **JDK 21**. The original upstream README below is retained for attribution and context; its JDK 17 and upstream clone instructions do not override this fork's build instructions.

---

<p align="center">
  <img src="docs/readme-banner.png" width="100%" alt="xnotes: a handwriting-first notebook for Android" />
</p>

<p align="center">
  <a href="https://github.com/shardulvs/xnotes-android/releases/latest"><img src="https://img.shields.io/github/v/release/shardulvs/xnotes-android?style=flat-square&label=release&color=blue" alt="Release" /></a>
  <a href="https://f-droid.org/en/packages/com.xnotes"><img src="https://img.shields.io/f-droid/v/com.xnotes?style=flat-square" alt="F-Droid" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-orange?style=flat-square" alt="License" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+" />
  <a href="https://github.com/sponsors/shardulvs"><img src="https://img.shields.io/badge/sponsor-%E2%9D%A4-ec6cb9?style=flat-square&logo=githubsponsors&logoColor=white" alt="Sponsor" /></a>
</p>

<p align="center">
  <a href="https://f-droid.org/en/packages/com.xnotes"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="70" alt="Get it on F-Droid" /></a>
  <a href="https://play.google.com/store/apps/details?id=com.xnotes"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="70" alt="Get it on Google Play" /></a>
</p>

<p align="center">
  <img src="docs/screenshots/editor.png" width="90%" alt="The xnotes editor with a PDF open in dark mode, annotated by hand with a note, an arrow, a highlight and an underline, and page thumbnails down the left" />
  <br />
  <em>Annotating a PDF in dark mode, with the pages inverted</em>
</p>

<p align="center">
  <img src="docs/screenshots/explorer.png" width="90%" alt="The xnotes file explorer in gallery view, showing a grid of imported book covers with page counts" />
  <br />
  <em>Browsing imported PDFs in the gallery view</em>
</p>

---

## Features

- **Ink that feels like a pen**: pressure-sensitive and low latency, with pens and highlighters.
- **Neon glow**: make any pen glow.
- **Disappearing ink**: ink that fades away on its own, great for teaching and presenting.
- **Tape**: cover answers with tape and tap to peel it back, for testing yourself.
- **Shape snapping**: draw a rough shape, hold, and it snaps clean.
- **Sharp at any zoom**: ink and PDFs stay crisp however far you zoom in.
- **Notebooks and an infinite canvas**: pages when you want structure, endless space when you don't.
- **Book layouts**: single, double or cover pages, scrolling or page flips.
- **PDF import**: bring in any PDF and write all over it.
- **Room for side notes**: widen a PDF's margins on any edge and take notes on the side.
- **Insert space**: drag anywhere on a page to open up room there, even in the middle of a PDF.
- **PDF text markups**: highlight, underline and strike out text, add comments, search it.
- **PDF colour filters**: invert, sepia, contrast, brightness, multiply and screen.
- **Images stay untouched**: filter a PDF and its photos and images keep their true colours.
- **Dark and OLED modes**: easy on the eyes at night, true black on OLED screens.
- **Real PDF export**: sharp vector output with selectable text, links and bookmarks.
- **Markdown**: write in markdown and it formats as you type.
- **Typed notes**: tables, LaTeX equations and code highlighting, right on the page.
- **Audio recording**: record a lecture as you write, and your ink replays in sync with it.
- **20+ page templates**: Cornell, planners, music staves, isometric and more.
- **Endless themes**: any accent colour, eight styles for each, dual tone and Material You.
- **Make it yours**: rearrange the toolbar, float it or dock it on any edge, and pick its size.
- **Split view**: two notes side by side.
- **A real file explorer**: grid, gallery, list, column and timeline views, with plenty to customise.
- **Your notes, your folders**: notes are saved as files in a folder you choose, not locked away in an app's database, so you stay in full control of them.
- **Works with stylus pens**: S Pen and many others.
- **Always editable**: nothing gets flattened, so every stroke can be moved, restyled or erased later.
- **Private**: open source, no account, no ads, no tracking, no internet access.

## Install

| Channel | |
|---|---|
| [GitHub Releases](https://github.com/shardulvs/xnotes-android/releases/latest) | Signed APK |
| [F-Droid](https://f-droid.org/en/packages/com.xnotes) | Built reproducibly from source |
| [Google Play](https://play.google.com/store/apps/details?id=com.xnotes) | Automatic updates |

GitHub and F-Droid ship the same signed APK, so you can switch between them without reinstalling.

## Build from source

Needs JDK 17, NDK 27.0.12077973 and CMake 3.22.1.

```bash
git clone https://github.com/shardulvs/xnotes-android.git
cd xnotes-android
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug
```

### MovinkPad Release 候选版（2026-11-02）

Debug 与 Release 均使用 com.xnotes.movinkpad。当前 Release 候选为
0.9.6-movinkpad-rc1 / versionCode 68，可通过显式
-PmovinkpadPreviewSigning=true 复用本机原 Debug 签名构建，以覆盖此前版本 67。
这不是新的生产密钥；跨机器必须核对签名，不能假定默认 Debug 密钥一致。
构建、覆盖验证与限制见 [Release 候选记录](docs/movinkpad/release-candidate-2026-11-02.md)。


### 独立双窗格测试版（2026-11-02）

使用 `-PsplitPreview=true` 构建时启用独立包名 `com.xnotes.movinkpad.splitpreview`，可与正式版并存；不传此属性保持既有包名。支持 PDF 笔记／普通笔记／无限画布混合并排，同一分页笔记可拆为两个独立阅读位置并共享标注与撤销。当前为 ARM64 Debug 测试版，尚未正式发布。

第二测试版新增直接选文件分屏、公共工具栏、单侧关闭、分隔线触控反馈与中心吸附，并修复共享文档缓存闪烁及四指手势识别。

第三测试版支持从当前文件夹开始逐层浏览与切换已授权位置，焦点标题改用名称下划线；图片／音频仍插入焦点文档。

第四测试版修复橡皮悬浮跨分割线与侧键持有交接；笔悬浮进入另一侧即同步工具并更新焦点。

第五测试版固定顶部通用工具栏，文档专属功能放在各自名称栏；支持长按名称拖动换位、保存最近分屏组合，以及主菜单往返。

使用方法、构建与验收见 [双窗格测试版记录](docs/movinkpad/split-preview-2026-11-02.md)。
