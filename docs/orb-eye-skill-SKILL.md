---
name: orb-eye
description: 通过本机 Orb Eye 控制当前手机界面（读屏、点击、输入、打开 App 等）。无需 Root。
trigger: 当用户要操作手机、看屏幕、点按、发帖、发图、输入文字、打开某个应用（如「打开小红书」「打开微信」）、或提到「手机」「小红书」「无障碍」「Orb Eye」时使用。
---

# Orb Eye 手机控制

**重要：你（OpenClaw 助理）运行在用户的本机安卓手机上；exec 工具在本机执行。用 orb.sh 或 curl http://127.0.0.1:7333 即可操作本机 Orb Eye，无需使用手机 IP。**

请用 **exec** 执行 `orb.sh` 完成用户请求。BotDrop 已将 `~/bin` 加入 PATH，直接写 `orb.sh` 即可。

## 常用命令

| 意图 | 执行命令 |
|------|----------|
| 看当前屏幕 | `orb.sh screen` 或 `orb.sh info` |
| 按文字点击 | `orb.sh click "按钮上的文字"` |
| 按坐标点击 | `orb.sh tap 540 1200` |
| 输入文字 | `orb.sh setText "要输入的内容"`（先让输入框获得焦点） |
| 等界面变化 | `orb.sh wait 5000` |
| 返回键 | `orb.sh back` |
| 回桌面 | `orb.sh home` |

## 打开某个 App（如「打开小红书」）

用户说「打开小红书」「打开微信」等时，按以下步骤用 exec 执行：

1. 先回桌面：`orb.sh home`
2. 等桌面出现：`orb.sh wait 2000`
3. 按应用名点击图标：`orb.sh click "小红书"`（或用户说的应用名，如「微信」「设置」）

若应用名在桌面上是英文或缩写，可根据 `orb.sh screen` 的结果再决定 click 的文本。若桌面有多页，可先 `orb.sh swipe 900 800 100 800 300` 滑到下一页再 click。

## 流程建议

- 先 `orb.sh screen` 或 `orb.sh info` 了解当前界面。
- 根据用户目标依次执行 click / setText，必要时 `orb.sh wait 3000` 再 screen。
- 点击优先用 `orb.sh click "文字"`；找不到再用 `/screen` 结果里的坐标 `orb.sh tap x y`。
