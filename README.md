# XNotes MovinkPad fork

This fork targets Wacom MovinkPad Pro 14 and Pro Pen 3. Three-button mappings and a black/white UI palette are implemented in the preview; target-device acceptance is pending. The upstream features below are not a claim of verified MovinkPad compatibility.

- [Preview scope and existing validation](MOVINKPAD.md)
- [Project brief](docs/movinkpad/project-brief.md)
- [Linux build and repository structure](docs/movinkpad/server-build.md)
- [Workspace switching and consistent pen settings](docs/movinkpad/workspace-switch-2026-10-31.md)
- [Branch split and upstream sync](docs/movinkpad/github-sync-2026-11-02.md)
- [Planned PDF and cursor work](tasks/plan.md)

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
