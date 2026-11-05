# 通用手写笔按键录入（2026-11-05）

## 范围

用户要求先审查 Key Mapper 源码，再结合现有笔输入实现录入映射；不保留旧 Pro Pen 3 配置，不打包，完成后在新远端分支存放并推送。分支：`codex/recordable-pen-shortcuts`。不合入 master、不安装或发布 APK。

设置 → 输入 → 手写笔按键映射 → 录入按键，在区域中按下并松开一个实体键，命名、选择功能与模式后保存。已有映射可以修改功能/模式或删除。录入有 15 秒时限，超时、取消或离开页面不保存。重复录入提示编辑已有项；最多 32 个映射，达到上限明确提示。

## Key Mapper 源码参考

固定审查版本：`65c398a0a11510878ce3bd8c6745a186364388e3`（develop）。

- [RecordTriggerController.kt](https://github.com/keymapperorg/KeyMapper/blob/65c398a0a11510878ce3bd8c6745a186364388e3/base/src/main/java/io/github/sds100/keymapper/base/trigger/RecordTriggerController.kt)：录入时维护按下集合，匹配键码、扫描码、deviceId、source；KeyEvent 松开时只有匹配按下才记录。开始清状态，结束注销 InputEventHub 监听，有录入时限。系统级 Accessibility/evdev 分支不引入本项目。
- [RecordedKey.kt](https://github.com/keymapperorg/KeyMapper/blob/65c398a0a11510878ce3bd8c6745a186364388e3/base/src/main/java/io/github/sds100/keymapper/base/trigger/RecordedKey.kt)：持久身份包含设备描述符，区别于运行时 ID。
- [RecordTriggerState.kt](https://github.com/keymapperorg/KeyMapper/blob/65c398a0a11510878ce3bd8c6745a186364388e3/base/src/main/java/io/github/sds100/keymapper/base/trigger/RecordTriggerState.kt) 和 [RecordTriggerButtonRow.kt](https://github.com/keymapperorg/KeyMapper/blob/65c398a0a11510878ce3bd8c6745a186364388e3/base/src/main/java/io/github/sds100/keymapper/base/trigger/RecordTriggerButtonRow.kt)：显式录入入口、录入状态与完成状态分离。

仅参考行为和设计；本功能独立实现，不复制 Key Mapper 源文件、不添加其运行库。其仓库标注 GPL-3.0，XNotes 现有许可证与第三方声明不变。

## 输入和保存边界

- `PenSignals` 将笔 MotionEvent 的按钮位及 KeyEvent（键码/扫描码/source/描述符）转换为同一种录入身份；不保存临时 deviceId。设备不提供描述符时用空描述符加来源匹配，无法区分同源且无描述符的设备。
- 一次按压可以记录多个上报信号（最多 8 个）；等所有信号释放后再等待 250 ms 收尾。同一映射的信号合并为一个按住状态，防止重叠上报重复执行。请一次只按一个实体键，多个同时按下的实体键无法自动区分于同键的多路上报。
- 只支持可获得按下/松开的输入。系统未投递的事件不能录入。返回、主页、电源、最近任务和 Esc 不录入，保留退出入口。笔尖接触、手指和鼠标移动不是笔侧键。
- 映射执行复用 `ProPenButtons` / `ProPenInput`；在落笔期间延迟工具/命令，取消笔画丢弃待执行项。MotionEvent 分屏沿用悬停交接去重；KeyEvent 松开送到所有打开窗格，防止焦点切换遗漏释放。
- 新偏好键 `pen_shortcuts`，不读取、不迁移 `pro_pen_bindings`。写入沿用 checked 原子保存，失败保留原设置；不修改笔记文件。未知/损坏映射加载为空，保存前拒绝无效、重复或超限数据。
- 录入监听只在可见设置页启动期间存在，结束、失焦和页面销毁时解除。普通编辑器只消费已录入的按键；原有非 Pro Pen 的手势设置仍保留。

## 验证

第一阶段：JDK 21 / Gradle 8.13，笔输入 JVM 测试通过，`:app:compileDebugAndroidTestKotlin` 通过。正在扩展 Android 事件对象主机测试与最终回归；尚未进行仪器执行、UI 真机验收或其他品牌笔的硬件验证。未打包 APK。
