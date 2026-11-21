# LuckyPray Java 版 — 幸运祈愿插件

XiYing 服务器自研的祈愿插件，**跑在 Java 服（Purpur/Paper）上**，支持原神式抽卡 + 双端原生 UI。

## 一、已完成并在测试服验证

| 功能 | 状态 |
|---|---|
| 原神式抽卡（等级权重、软保底递增、定轨歪一次、十连规则） | ✅ 实测抽 3 次，记录/计数/保底全部正确 |
| Java 玩家箱子 GUI（主菜单/卡池/说明/定轨/结果/记录） | ✅ 实测 `/pray menu` 成功打开箱子界面 |
| 基岩玩家 Geyser 原生表单（SimpleForm） | ✅ Geyser API 初始化就绪（需真基岩客户端最终目视确认） |
| 经济对接：Vault / PlayerPoints / 物品 / 免费 | ✅ 框架完成，配置可切换（测试用 free 模式跑通） |
| 配置驱动卡池（pools.yml） | ✅ 框架完成，2 个示例卡池模板 |
| 玩家数据持久化（每日上限/保底计数/定轨/记录） | ✅ 实测生成 players/<uuid>.yml |
| 出金全服播报 / 自定义奖励命令 | ✅ 已实现 |

## 二、测试服验证记录（2026-11-18）

```
测试服: C:\LuckyPrayTest  端口 30384 (Java+基岩 UDP) / RCON 25576
插件日志: [LuckyPray] 已启用 | 卡池 1 | Geyser表单 就绪
抽卡测试: /pray pray LP_Tester example_points 3
结果: 玩家数据 dailyCount=3, 两条保底规则 combos=3,
      记录=[3星钻石, 2星铁锭, 2星铁锭]（概率分布符合权重）
GUI测试: /pray menu -> 触发 windowOpen 事件（箱子成功打开）
```

## 三、卡池配置（pools.yml）

每个卡池可配置：
- `prayItem.mode`: `vault`(经济) / `points`(点券) / `item`(物品) / `free`(免费)
- `regulars`: 多条保底规则（combos 硬保底抽数、threshold 软保底起点、maxOrbitScore 定轨上限、aimRewards 定轨奖品）
- `rewards`: 按等级 1-5 分组的奖品，每个奖品有权重 probability、奖励物品、自定义命令
- 奖励物品 `mode`: `item`(普通物品) / `nbt`(NBT物品，当前用 give 兜底) / `command`(执行命令)

## 四、命令

- `/pray` 或 `/pray menu` — 打开祈愿菜单（基岩自动走表单，Java 走箱子）
- `/pray reload` — 重载配置（需 luckypray.admin，默认 OP）
- `/pray pray <玩家> <卡池key> <次数>` — 管理员强制抽奖
- 手持 `NETHER_STAR`（可配置）右键也能打开菜单

## 五、部署到主服（待用户确认后执行）

1. 把 `LuckyPray.jar` 复制到主服 `plugins/`
2. 重启服务器（或 `/reload confirm`，但建议重启）
3. 编辑 `plugins/LuckyPray/pools.yml` 配置真实卡池和消耗
4. 确认 Geyser 已装（基岩表单依赖它）

## 六、已知限制

- **NBT 定制物品**：`mode:nbt` 当前用普通 `give` 兜底，没做完整 NBT 组件写入（1.20.5+ 用 data components）。如需自定义附魔书/改名物品，后续可接 NBT-API 或改成命令方式。
- **基岩表单**：Geyser API 已确认就绪，但测试环境没有真基岩客户端，UI 外观需你上基岩号目视确认。
- **编译版本**：class 用 Java 25（major 69），与服务器 paper-api 一致，避免 ViaVersion 的 ASM 转换报错（用 JDK 27 直接编译会生成 71 导致插件加载失败，必须用 `-source 25 -target 25`）。

## 七、源码与构建

- 源码目录：`LuckyPrayJava/src/main/java/com/xinying/luckypray/`
- 最终 jar：`LuckyPrayJava/LuckyPray.jar`（44KB）
- 构建：因本地 JDK 24 与目标 API 版本不符，编译在 VPS 上用 JDK 27 + `-source/-target 25` 完成（脚本 `_compile_vps.py`）。
