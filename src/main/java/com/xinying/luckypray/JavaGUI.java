package com.xinying.luckypray;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Java 玩家箱子 GUI */
public class JavaGUI implements InventoryHolder {

    public enum Kind { MAIN, POOL, RESULT, RECORDS, AIM }
    public Kind kind;
    public String poolKey = "";
    public Player player;
    public List<DrawResult> lastResults = new ArrayList<>();

    private final LuckyPray plugin;
    private Inventory inv;

    public JavaGUI(LuckyPray plugin, Player player, Kind kind) {
        this.plugin = plugin;
        this.player = player;
        this.kind = kind;
    }

    @Override public Inventory getInventory() { return inv; }

    public void open() {
        switch (kind) {
            case MAIN: openMain(); break;
            case POOL: openPool(); break;
            case RESULT: openResult(); break;
            case RECORDS: openRecords(); break;
            case AIM: openAim(); break;
        }
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack is = new ItemStack(m);
        ItemMeta im = is.getItemMeta();
        if (im != null) {
            im.setDisplayName(C.color(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(C.color(s));
            im.setLore(l);
            is.setItemMeta(im);
        }
        return is;
    }

    private void openMain() {
        List<Pool> pools = plugin.enabledPools();
        int size = Math.max(9, ((pools.size() + 1 + 8) / 9) * 9);
        inv = Bukkit.createInventory(this, Math.min(54, size), C.color("&c&l❀ 幸运祈愿 ❀"));
        int i = 0;
        for (Pool p : pools) {
            Material m = C.material(p.icon, Material.NETHER_STAR);
            inv.setItem(i++, item(m, p.name,
                    "&7" + strip(p.description),
                    "&e今日剩余: &6" + plugin.remainingText(player, p),
                    "&8点击选择卡池"));
        }
        inv.setItem(size - 1, item(Material.WRITABLE_BOOK, "&b&l祈愿记录", "&7查看最近祈愿"));
        player.openInventory(inv);
    }

    private void openPool() {
        Pool p = plugin.getPool(poolKey);
        if (p == null) return;
        inv = Bukkit.createInventory(this, 27, C.color("&c&l祈愿 - " + p.name));
        inv.setItem(4, item(C.material(p.icon, Material.NETHER_STAR), p.name,
                "&7" + strip(p.description),
                "&e剩余次数: &6" + plugin.remainingText(player, p),
                "&7消耗: &6" + p.costAmount + " " + p.costName + " &7(" + p.costMode + ")",
                "&7余额: &6" + plugin.economy.balanceText(player, p.costMode, p.costItem)));

        int slot = 10;
        for (Map.Entry<String, Integer> e : p.prayModes.entrySet()) {
            String label = e.getKey();
            int count = e.getValue();
            Material m = count >= 10 ? Material.DIAMOND_BLOCK : Material.DIAMOND;
            inv.setItem(slot++, item(m, label,
                    "&7消耗 &6" + (p.costAmount * count) + " " + p.costName,
                    "&e点击祈愿 " + count + " 次"));
        }
        inv.setItem(16, item(Material.BOOK, "&b&l祈愿说明", "&7概率 / 保底 / 定轨"));
        inv.setItem(22, item(Material.ENDER_PEARL, "&d&l祈愿定轨", "&7选择保底目标"));
        inv.setItem(18, item(Material.ARROW, "&7返回"));
        player.openInventory(inv);
    }

    private void openResult() {
        inv = Bukkit.createInventory(this, 27, C.color("&d&l✧ 祈愿结果 ✧"));
        for (int k = 0; k < Math.min(10, lastResults.size()); k++) {
            DrawResult r = lastResults.get(k);
            LevelDef ld = plugin.getLevel(r.level);
            String ln = ld == null ? r.level : ld.name;
            String lc = ld == null ? "&f" : ld.color;
            inv.setItem(9 + k, item(Material.NETHER_STAR, lc + ln + " &r" + (r.reward == null ? "?" : r.reward.name),
                    "&7等级 " + r.level + (r.hitAim ? " &a[定轨命中]" : ""),
                    "&8" + (r.reward == null ? "" : strip(r.reward.description))));
        }
        inv.setItem(26, item(Material.ARROW, "&7返回卡池"));
        player.openInventory(inv);
    }

    private void openRecords() {
        inv = Bukkit.createInventory(this, 54, C.color("&b&l祈愿记录"));
        PlayerState st = plugin.state(player);
        for (int k = 0; k < Math.min(45, st.records.size()); k++)
            inv.setItem(k, item(Material.PAPER, "&7#" + (k + 1), "&f" + st.records.get(k)));
        inv.setItem(53, item(Material.ARROW, "&7返回"));
        player.openInventory(inv);
    }

    private void openAim() {
        Pool p = plugin.getPool(poolKey);
        if (p == null) return;
        inv = Bukkit.createInventory(this, 27, C.color("&d&l定轨选择"));
        // 列出最高等级的所有奖品供选择
        String top = highestLevel(p);
        int slot = 0;
        for (Reward r : p.rewardsOf(top)) {
            if (slot >= 21) break;
            inv.setItem(slot++, item(Material.DIAMOND, r.name, "&7" + strip(r.description), "&8index: " + r.index));
        }
        inv.setItem(26, item(Material.ARROW, "&7返回"));
        player.openInventory(inv);
    }

    private String highestLevel(Pool p) {
        String best = null; int b = -1;
        for (String lv : p.rewards.keySet()) {
            int n = 0; try { n = Integer.parseInt(lv); } catch (Exception ignored) {}
            if (n > b) { b = n; best = lv; }
        }
        return best == null ? "5" : best;
    }

    /** 点击处理，返回 true 表示已消费 */
    public boolean onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return false;
        ItemStack it = inv.getItem(slot);
        if (it == null || it.getType() == Material.AIR) return true;
        String dn = it.getItemMeta() != null ? it.getItemMeta().getDisplayName() : "";
        dn = C.plain(dn);

        switch (kind) {
            case MAIN:
                if (slot == inv.getSize() - 1) { new JavaGUI(plugin, player, Kind.RECORDS).open(); return true; }
                { List<Pool> pools = plugin.enabledPools();
                  if (slot < pools.size()) {
                      JavaGUI g = new JavaGUI(plugin, player, Kind.POOL);
                      g.poolKey = pools.get(slot).key;
                      g.open();
                  } }
                return true;
            case POOL:
                if (slot == 18 || dn.contains("返回")) { new JavaGUI(plugin, player, Kind.MAIN).open(); return true; }
                if (slot == 16 || dn.contains("说明")) { player.sendMessage(plugin.helpMessages(poolKey)); return true; }
                if (slot == 22 || dn.contains("定轨")) { JavaGUI g = new JavaGUI(plugin, player, Kind.AIM); g.poolKey = poolKey; g.open(); return true; }
                // 抽卡按钮从 slot10 开始
                { Pool p = plugin.getPool(poolKey);
                  int idx = slot - 10;
                  int n = 0;
                  for (Map.Entry<String, Integer> en : p.prayModes.entrySet()) {
                      if (n == idx) { plugin.doDraw(player, p, en.getValue(), false); return true; }
                      n++;
                  } }
                return true;
            case RESULT:
                if (slot == 26 || dn.contains("返回")) { JavaGUI g = new JavaGUI(plugin, player, Kind.POOL); g.poolKey = poolKey; g.open(); return true; }
                return true;
            case RECORDS:
                if (slot == 53 || dn.contains("返回")) { new JavaGUI(plugin, player, Kind.MAIN).open(); return true; }
                return true;
            case AIM:
                if (slot == 26 || dn.contains("返回")) { JavaGUI g = new JavaGUI(plugin, player, Kind.POOL); g.poolKey = poolKey; g.open(); return true; }
                { Pool p = plugin.getPool(poolKey);
                  String top = highestLevel(p);
                  int idx = slot;
                  List<Reward> rs = p.rewardsOf(top);
                  if (idx >= 0 && idx < rs.size()) {
                      PluginAim.setAim(player, p, top, rs.get(idx).index);
                      player.sendMessage(C.color("&d[祈愿] 已设置定轨: &f" + rs.get(idx).name));
                      JavaGUI g = new JavaGUI(plugin, player, Kind.POOL); g.poolKey = poolKey; g.open();
                  } }
                return true;
        }
        return true;
    }

    static String strip(String s) {
        if (s == null) return "";
        return C.plain(s);
    }

    /** 定轨辅助：写到玩家状态 */
    static class PluginAim {
        static void setAim(Player p, Pool pool, String level, String index) {
            // 定轨写在最高优先级规则的 aimRewards 里
            PlayerState st = LuckyPray.getInstance().state(p);
            for (PityRule rule : pool.rules) {
                if (rule.level.equals(level)) {
                    PlayerState.RuleState rs = st.rule(pool.key, rule.name);
                    rs.aimRewards.clear();
                    rs.aimRewards.add(index);
                    rs.orbitScore = 0;
                }
            }
            LuckyPray.getInstance().saveState(p);
        }
    }
}
