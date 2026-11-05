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
- 一次按压可以记录多个上报信号（最多 8 个）；等所有信号释放后再等待 250 ms 收尾。执行时选定一个主信号：优先笔按钮状态，否则使用第一个 KeyEvent。额外 KeyEvent 仅消费，不更新状态，不复活已暂停的工具，也不撤销待执行动作。这样重叠或先后到达的别名都不会重复执行，且不对真实连续按压节流。请一次只按一个实体键，多个同时按下的实体键无法自动区分于同键的多路上报。含 MotionEvent 的映射需要笔靠近屏幕；遥控使用需在远离屏幕时录入 KeyEvent-only 映射。
- 只支持可获得按下/松开的输入。系统未投递的事件不能录入。返回、主页、电源、最近任务和 Esc 不录入，保留退出入口。笔尖接触、手指和鼠标移动不是笔侧键。
- 映射执行复用 `ProPenButtons` / `ProPenInput`；在落笔期间延迟工具/命令，取消笔画丢弃待执行项。MotionEvent 分屏沿用悬停交接去重；KeyEvent 按住身份也参与窗格交接，只重挂临时工具、不重执行命令；松开送到所有打开窗格。窗口失焦清除 KeyEvent 边沿以接收下一次新按下，MotionEvent 保留原有交接去重。
- 新偏好键 `pen_shortcuts`，不读取、不迁移 `pro_pen_bindings`。写入沿用 checked 原子保存，失败保留原设置；不修改笔记文件。未知/损坏映射加载为空，保存前拒绝无效、重复或超限数据。
- 录入监听只在可见设置页启动期间存在，结束、失焦和页面销毁时解除。普通编辑器只消费已录入的按键；原有非 Pro Pen 的手势设置仍保留。

## 验证

JDK 21 / Gradle 8.13，完整主机测试 **2618 项通过，0 失败、0 错误、0 跳过**。其中 `PenInputHostTest` 使用 Robolectric 4.17 / Android API 35 执行 17 项 MotionEvent/KeyEvent 契约；同一份 `src/sharedTest` 契约也接入 `PenTestRunner` 的设备仪器入口。`:app:compileDebugAndroidTestKotlin` 通过。

覆盖：任意按钮位、不同 source 的笔事件、重复按下、未绑定键透传、工具按住/恢复、笔画内延迟、笔画取消和按键取消、分屏交接与命令去重、失焦后漏收松开的恢复、手指/鼠标/笔尖排除、系统导航排除、重叠及非重叠别名。录入状态、旧配置不迁移、新配置往返、重复/超限拒绝、原子写失败保留目的地也有 JVM 回归。原 3907 条 MovinkPad 事件轨迹仍通过。

独立源码审查发现并修复了 KeyEvent 按住跨分屏丢失、失焦后下一次按压被吞、非重叠别名重复执行、别名松开复活已暂停工具。最后一项保留了修复前测试失败、修复后通过的证据。`git diff --check` 通过。

复现（首次运行需下载仅测试使用的 Robolectric/Android 运行库；不会生成 APK）：

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

**未执行设备仪器测试、未验收实际 UI 布局、未验证其他品牌笔或目标设备硬件兼容性。** Robolectric 是主机模拟，不能替代上述验证。未打包 APK、未安装设备。
