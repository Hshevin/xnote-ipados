# MovinkPad preview development

- Read `MOVINKPAD.md` and `README.md` before changing this fork. Read `docs/movinkpad/project-brief.md` and check `git status` before work. This fork is self-contained; the separate diagnostic application is not a module here.
- Preserve upstream licenses and third-party notices. Do not commit or push unless requested.
- Target Wacom MovinkPad Pro 14, Android 15, Pro Pen 3; product reference remains OneNote Android tablet.
- Keep everything local/offline. No account, sync or backend requirements.
- Keep physical button numbering `0x20`, `0x40`, `0x04`. Detect edges from touch and hover buttonState, deduplicate independent button events, never hardcode device IDs.
- `pen/ProPenButtons` is the pure state machine; `pen/ProPenInput` adapts Android events for both editor surfaces. Preserve cancelled strokes and tool settings; do not change tools midway through a stroke.
- Build with JDK 21 and this repository's wrapper/toolchain. Deliver ARM64 APK without injected IDE/testOnly flags.
- Run relevant unit and Android event tests; hardware acceptance must separately record device/system/pen details. Do not claim hardware compatibility from emulator results.
- If a Python environment is needed, create it with uv.

- UI uses pure white #FFFFFF / black #000000, inverted in night mode; no coloured accents, gradients or decorative shadows. Full visual and target-device acceptance remains pending.
- PDF source and read-only imported originals must remain unchanged; annotations and disposable display caches are separate. Large scanned-PDF and tool-cursor work is planned in `tasks/plan.md` and `tasks/todo.md`, not completed.
- Notes must persist locally. Verify recovery and failure paths for storage, import, deletion and migration changes.
- Preserve raw button masks and axes. Compare buttonState transitions on MOVE/HOVER_MOVE and deduplicate independent button events; distinguish stylus, finger and cancellation.
- Use JDK 21, Gradle wrapper 8.13, AGP 8.12.3, SDK/Build Tools 36/36.0.0, NDK 27.0.12077973 and CMake 3.22.1. Do not import the diagnostic project's AGP 8.12.0 setting.
- Preserve user changes; no commit, push, remote or Git configuration changes unless requested. Temporary files belong in ignored `tmp/`, not source or permanent documentation.

- 用户于 2026-10-31 最新要求本次直接在本机编译，覆盖此前“不在本机编译”的约定；使用 JDK 21，遵守执行环境权限。未经明确要求仍不提交或推送。

## 文档维护

- 每完成一个功能阶段、修复一组问题或准备交付时，检查并按需同步 `AGENTS.md` 与 `README.md`；长期任务在阶段完成时同步，不积压到最终交付。
- `AGENTS.md` 记录长期有效的协作约定、开发边界、工具链和验证要求；`README.md` 记录当前项目状态、构建/测试入口、测试版与正式版的区别及详细文档链接。
- 详细变更和验收证据放入 `docs/`，入口文档保持简洁；明确区分已实现、已验证、待真机验证及讨论中方案，无变化时不为更新而改写。
- 外层工作区与 `xnotes/` 独立仓库各自维护对应说明；原型功能以 `xnotes/` 为准，涉及工作区入口或仓库分工时同步外层文档。

## 覆盖安装与 Release

- 用户于 2026-11-02 确认当前分屏版本足够，授权打包覆盖早期 XNotes 的 Release；交付包使用 `com.xnotes.movinkpad`，独立测试包仍为 `com.xnotes.movinkpad.splitpreview`。不把打包授权扩展为 GitHub 推送或商店发布。
- 覆盖升级必须沿用已交付 APK 的签名、提高正式包 versionCode，核对签名与安装身份。当前通过 `-PmovinkpadPreviewSigning=true` 显式沿用历史本机 Debug 密钥；Release 仍启用 R8、资源压缩并关闭 debuggable。不要重新生成密钥或要求用户卸载旧版。

- 主菜单打开普通文档必须进入单文档模式；窗格内首页打开文件只替换所在窗格；只有显式打开/恢复分屏才恢复双窗格。替换工作区前等待保存完成，避免异步关闭回调误关新文档；未打开成功不得因预检查失败丢弃当前工作区。
