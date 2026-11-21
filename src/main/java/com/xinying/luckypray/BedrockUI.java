package com.xinying.luckypray;

import org.bukkit.entity.Player;

import java.util.*;

/** 基岩玩家 Geyser 原生表单 UI（通过 BedrockBridge 反射构建，避免类冲突） */
public class BedrockUI {
    private final LuckyPray plugin;
    private final BedrockBridge bridge;

    public BedrockUI(LuckyPray plugin, BedrockBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void openMain(Player p) {
        List<Pool> pools = plugin.enabledPools();
        List<String> buttons = new ArrayList<>();
        for (Pool pool : pools) {
            buttons.add(C.plain(pool.name) + "\n§7(今日剩余 §6" + plugin.remainingText(p, pool) + "§7)");
        }
        buttons.add("§b§l祈愿记录");
        final List<Pool> poolsFinal = pools;
        bridge.openSimpleForm(p,
                C.color("&c&l❀ 幸运祈愿 ❀"),
                "\n" + C.color("&b选择一个祈愿池:") + "\n",
                buttons,
                (form, res) -> {
                    int id = bridge.clickedButton(res);
                    if (id < 0) return;
                    if (id == poolsFinal.size()) openRecords(p);
                    else if (id < poolsFinal.size()) openPool(p, poolsFinal.get(id).key);
                });
    }

    public void openPool(Player p, String poolKey) {
        Pool pool = plugin.getPool(poolKey);
        if (pool == null) return;
        String content = C.color("\n" + pool.description) + "\n\n"
                + C.color("&e剩余: &6" + plugin.remainingText(p, pool)) + "\n"
                + C.color("&7消耗: &6" + pool.costAmount + " " + pool.costName + " (" + pool.costMode + ")") + "\n"
                + C.color("&7余额: &6" + plugin.economy.balanceText(p, pool.costMode, pool.costItem)) + "\n";
        List<String> modeKeys = new ArrayList<>(pool.prayModes.keySet());
        List<String> buttons = new ArrayList<>();
        for (String k : modeKeys) buttons.add(C.plain(k));
        buttons.add("§b祈愿说明");
        buttons.add("§d祈愿定轨");
        buttons.add("§7返回");
        bridge.openSimpleForm(p, C.plain(pool.name), content, buttons, (form, res) -> {
            int id = bridge.clickedButton(res);
            if (id < 0) return;
            if (id < modeKeys.size()) {
                plugin.doDraw(p, pool, pool.prayModes.get(modeKeys.get(id)), true);
            } else if (id == modeKeys.size()) {
                p.sendMessage(plugin.helpMessages(poolKey));
            } else if (id == modeKeys.size() + 1) {
                openAim(p, poolKey);
            } else {
                openMain(p);
            }
        });
    }

    public void openAim(Player p, String poolKey) {
        Pool pool = plugin.getPool(poolKey);
        if (pool == null) return;
        String top = highestLevel(pool);
        List<Reward> rewards = pool.rewardsOf(top);
        List<String> buttons = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        for (Reward r : rewards) {
            buttons.add(C.plain(r.name));
            indexes.add(r.index);
        }
        buttons.add("§7返回");
        bridge.openSimpleForm(p, "§d§l祈愿定轨", "§7选择最高等级的定轨奖品：\n", buttons, (form, res) -> {
            int id = bridge.clickedButton(res);
            if (id < 0) return;
            if (id < indexes.size()) {
                JavaGUI.PluginAim.setAim(p, pool, top, indexes.get(id));
                p.sendMessage(C.color("&d[祈愿] 已设置定轨: &f" + rewards.get(id).name));
            }
            openPool(p, poolKey);
        });
    }

    public void openRecords(Player p) {
        PlayerState st = plugin.state(p);
        StringBuilder sb = new StringBuilder("§b§l祈愿记录\n\n");
        List<String> rec = st.records;
        if (rec.isEmpty()) sb.append("§7暂无祈愿记录");
        else for (int i = 0; i < Math.min(20, rec.size()); i++)
            sb.append("§6#").append(i + 1).append(" §r").append(C.plain(rec.get(i))).append("\n");
        bridge.openSimpleForm(p, "§b§l祈愿记录", sb.toString(),
                Collections.singletonList("§7返回"), (form, res) -> openMain(p));
    }

    /** 抽奖结果展示 */
    public void showResult(Player p, String poolKey, List<DrawResult> results) {
        StringBuilder sb = new StringBuilder();
        for (DrawResult r : results) {
            LevelDef ld = plugin.getLevel(r.level);
            String ln = ld == null ? r.level : ld.name;
            String lc = ld == null ? "&f" : ld.color;
            sb.append(C.color(lc + ln)).append(" ").append(C.plain(r.reward == null ? "?" : r.reward.name));
            if (r.hitAim) sb.append(" §a[定轨]");
            sb.append("\n");
        }
        bridge.openSimpleForm(p, "§d§l✧ 祈愿结果 ✧", sb.toString(),
                Arrays.asList("§a继续祈愿", "§7返回主菜单"), (form, res) -> {
                    if (bridge.clickedButton(res) == 0) openPool(p, poolKey);
                    else openMain(p);
                });
    }

    private String highestLevel(Pool pool) {
        String best = null; int b = -1;
        for (String lv : pool.rewards.keySet()) {
            int n = 0; try { n = Integer.parseInt(lv); } catch (Exception ignored) {}
            if (n > b) { b = n; best = lv; }
        }
        return best == null ? "5" : best;
    }
}
