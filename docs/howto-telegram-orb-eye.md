# 用 Telegram 发指令让 AI 操作手机（BotDrop + Orb Eye）

不 Root，用「BotDrop（大脑）+ Orb Eye（手）」在 Telegram 里聊天就能让 AI 操作当前屏幕：读屏、点击、输入文字等（例如在小红书发图、点评论）。

---

## 一、前置条件

1. **同一台手机**已安装并配置好：
   - **BotDrop**：Telegram 已配对，能和 Bot 正常对话。
   - **Orb Eye**：已安装并开启无障碍（设置 → 无障碍 → Orb Eye → 开）。
2. 在 BotDrop 里打开 **Open Terminal**，执行：
   ```bash
   curl -s http://127.0.0.1:7333/ping
   ```
   若看到 `{"ok":true,"version":"2.0"}` 说明 Orb Eye 正常。若连不上，检查 Orb Eye 是否已开启、是否被省电/后台限制。

---

## 二、把「手」接到「大脑」：脚本 + 技能

让 OpenClaw 知道可以靠 **exec** 执行脚本调用 Orb Eye。

### 2.1 `orb.sh` 脚本（应用自动安装）

BotDrop 应用在**启动时**和**每次启动网关时**会自动把内置的 `orb.sh` 复制到 `~/bin/orb.sh`，并把 `~/bin` 加入 PATH。因此：

- **无需手动创建** `orb.sh`，只要安装/更新 BotDrop 并打开过应用（或启动过网关），`~/bin/orb.sh` 就已存在且可执行。
- 在 **Open Terminal** 或由 OpenClaw 的 exec 调用时，直接使用 `orb.sh` 即可（例如 `orb.sh screen`、`orb.sh click "发笔记"`）。

若你自行修改过 `~/bin/orb.sh`，重启网关或重新打开应用后会被覆盖为应用内置版本；若要保留自定义，可把脚本放在其他目录并改名为 `orb-custom.sh` 等，在技能里写全路径调用。

### 2.2 创建 OpenClaw 技能，让 AI 会「用手」

在 **Open Terminal** 里执行：

```bash
mkdir -p ~/.openclaw/skills/orb-eye
```

然后创建文件 `~/.openclaw/skills/orb-eye/SKILL.md`。**完整内容见项目里的 [docs/orb-eye-skill-SKILL.md](orb-eye-skill-SKILL.md)**，可整段复制到手机（用 BotDrop 里的编辑器或 adb 写入）。该技能里已写明：

- 你运行在本机，exec 在本机执行，用 `orb.sh` 操作 Orb Eye 即可。
- **打开某个 App**（如「打开小红书」）：先 `orb.sh home` → `orb.sh wait 2000` → `orb.sh click "小红书"`。
- 其他：看屏、点击、输入、返回等命令用法。

### 2.3 启用技能并重启网关

在 `~/.openclaw/openclaw.json` 里确保技能被启用。若已有 `skills.entries`，增加一项；若没有，可参考下面片段（只保留与 skills 相关的部分，其余键不要删）：

```json
"skills": {
  "entries": {
    "orb-eye": { "enabled": true }
  }
}
```

保存后，在 BotDrop **Dashboard** 里点 **Restart** 重启网关。

---

## 三、在 Telegram 里怎么发指令

和平时一样**直接和 Bot 聊天**，用自然语言说你想让手机做什么即可。例如：

| 你说的话（示例） | AI 会做的事 |
|------------------|-------------|
| **打开小红书** | `orb.sh home` → `orb.sh wait 2000` → `orb.sh click "小红书"` |
| 看一下当前屏幕 | 调用 `orb.sh screen` 或 `orb.sh info`，把结果总结给你 |
| 点一下「发笔记」 | 调用 `orb.sh click "发笔记"` |
| 在输入框里写「写得真好」 | 先可能点输入框，再 `orb.sh setText "写得真好"` |
| 等 3 秒再看一次屏幕 | `orb.sh wait 3000` 再 `orb.sh screen` |
| 在小红书发一张图片 | 按步骤：看当前是否在小红书 → 找发笔记/图片入口 → 点击 → 等界面 → 再找选图/发布按钮并点击 |

**不需要**在对话里写命令或脚本名，只要说清楚目标（例如「点一下屏幕上的发送」「把评论改成 XXX」），AI 会自己决定调用哪些 `orb.sh` 子命令。

---

## 四、小结

1. **确认**：BotDrop + Telegram 正常，Orb Eye 已开，`curl http://127.0.0.1:7333/ping` 正常。
2. **脚本**：BotDrop 会自动把 `orb.sh` 放到 `~/bin` 并加入 PATH，无需手拷。
3. **加技能**：在 `~/.openclaw/skills/orb-eye/SKILL.md` 写好说明，并在 `openclaw.json` 里启用 `orb-eye`，重启网关。
4. **发指令**：在 Telegram 里用自然语言说「看屏幕」「点 XXX」「输入 XXX」「在小红书发一张图」等即可。

这样就能在不 Root 的前提下，通过 Telegram 和 AI 聊天控制手机操作。
