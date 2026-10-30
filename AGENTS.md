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

- 用户于 2026-10-31 要求：不在本机编译；最终构建由用户在 Linux 服务器完成。后续变更按用户授权提交／推送并交付服务器验证命令，不自行启动本地 Gradle 构建。
