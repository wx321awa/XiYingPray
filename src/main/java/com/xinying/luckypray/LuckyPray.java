package com.xinying.luckypray;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.*;

public class LuckyPray extends JavaPlugin implements Listener, TabExecutor {

    private static LuckyPray instance;
    public static LuckyPray getInstance() { return instance; }

    private final Map<String, Pool> pools = new LinkedHashMap<>();
    private final Map<String, LevelDef> levels = new LinkedHashMap<>();
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<UUID> drawing = new HashSet<>();

    public Economy economy;
    public BedrockBridge bedrock;
    public BedrockUI bedrockUI;
    public DrawEngine engine;

    private String broadcastLevel = "5";
    private String openItem = "NETHER_STAR";
    private boolean soundEnabled = true;
    private final Map<String, PlayerState> stateFiles = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        loadConfigs();

        economy = new Economy(); economy.init();
        bedrock = new BedrockBridge(this); bedrock.init();
        bedrockUI = new BedrockUI(this, bedrock);
        engine = new DrawEngine(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("pray").setExecutor(this);
        getCommand("pray").setTabCompleter(this);

        getLogger().info("[LuckyPray] 已启用 | 卡池 " + pools.size() + " | Geyser表单 "
                + (bedrock.formReady() ? "就绪" : "未就绪"));
    }

    @Override public void onDisable() { saveAllStates(); instance = null; }

    /** 加载 config.yml / pools.yml */
    public void loadConfigs() {
        pools.clear(); levels.clear();
        File cf = configFile();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(cf);
        broadcastLevel = c.getString("broadcastLevel", "5");
        openItem = c.getString("guiItem", "NETHER_STAR");
        soundEnabled = c.getBoolean("sound", true);

        ConfigurationSection lvSec = c.getConfigurationSection("level");
        if (lvSec != null) for (String k : lvSec.getKeys(false)) {
            LevelDef l = LevelDef.fromMap(lvSec.getConfigurationSection(k).getValues(false));
            l.index = k;
            levels.put(k, l);
        }

        File pf = new File(getDataFolder(), "pools.yml");
        if (!pf.exists()) saveResource("pools.yml", false);
        YamlConfiguration pc = YamlConfiguration.loadConfiguration(pf);
        ConfigurationSection ps = pc.getConfigurationSection("pools");
        if (ps != null) for (String k : ps.getKeys(false))
            pools.put(k, Pool.fromMap(k, ps.getConfigurationSection(k).getValues(false)));
    }

    private File configFile() {
        File f = new File(getDataFolder(), "config.yml");
        if (!f.exists()) saveDefaultConfig();
        return f;
    }

    public LevelDef getLevel(String index) { return levels.get(index); }
    public Pool getPool(String key) { return pools.get(key); }
    public List<Pool> enabledPools() {
        List<Pool> out = new ArrayList<>();
        for (Pool p : pools.values()) if (p.enabled && !p.expired()) out.add(p);
        return out;
    }

    /**
     * 剩余次数：无上限返回 -1。
     * 同时按池的 resetHour/resetMinute 做水位线重置（早于最近重置点则清零该池计数）。
     */
    public int remainingCount(PlayerState st, Pool pool) {
        if (pool.unlimitedDaily()) return -1;
        if (pool.dailyReset) {
            long rp = lastResetPoint(pool.resetHour, pool.resetMinute);
            Long last = st.lastReset.get(pool.key);
            if (last == null || last < rp) {
                st.dailyCount.put(pool.key, 0);
                st.lastReset.put(pool.key, rp);
            }
        }
        return Math.max(0, pool.maxDaily - st.dailyCount.getOrDefault(pool.key, 0));
    }

    /** 最近一次重置时刻（今天或昨天，取早于当前的那个） */
    private long lastResetPoint(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, -1);
        return c.getTimeInMillis();
    }

    /** UI 用剩余次数文本 */
    public String remainingText(Player p, Pool pool) {
        int r = remainingCount(state(p), pool);
        return r < 0 ? "无限" : String.valueOf(r);
    }

    /** 玩家状态（带每日重置） */
    public PlayerState state(Player p) {
        PlayerState s = states.get(p.getUniqueId());
        if (s == null) {
            s = PlayerState.load(stateFile(p));
            s.uuid = p.getUniqueId().toString();
            s.name = p.getName();
            states.put(p.getUniqueId(), s);
        }
        checkDaily(s);
        return s;
    }

    private void checkDaily(PlayerState s) {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
        if (!today.equals(s.dailyDate)) {
            s.dailyDate = today;
            for (String k : new ArrayList<>(s.dailyCount.keySet())) s.dailyCount.put(k, 0);
        }
    }

    private File stateFile(Player p) {
        return new File(new File(getDataFolder(), "players"), p.getUniqueId() + ".yml");
    }

    public void saveState(Player p) {
        PlayerState s = states.get(p.getUniqueId());
        if (s == null) return;
        File f = stateFile(p);
        f.getParentFile().mkdirs();
        s.save(f);
    }

    private void saveAllStates() {
        for (UUID u : states.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) saveState(p);
        }
    }

    /** 打开主菜单：基岩走表单，Java 走箱子 */
    public void openMenu(Player p) {
        boolean b = bedrock.isBedrock(p);
        getLogger().info("[LuckyPray] openMenu " + p.getName() + " uuid=" + p.getUniqueId() + " bedrock=" + b);
        if (b) {
            bedrockUI.openMain(p);
        } else {
            new JavaGUI(this, p, JavaGUI.Kind.MAIN).open();
        }
    }

    /** 执行一次祈愿（count 次） */
    public void doDraw(Player p, Pool pool, int count, boolean bedrockClient) {
        if (drawing.contains(p.getUniqueId())) {
            p.sendMessage(C.color("&c[祈愿] 少女祈祷中，请稍候..."));
            return;
        }
        if (!pool.enabled) { p.sendMessage(C.color("&c[祈愿] 该卡池已关闭")); return; }
        if (pool.expired()) { p.sendMessage(C.color("&c[祈愿] 该卡池已截止")); return; }

        PlayerState st = state(p);
        int used = 0;
        if (!pool.unlimitedDaily()) {
            used = st.dailyCount.getOrDefault(pool.key, 0);
            if (remainingCount(st, pool) <= 0) { p.sendMessage(C.color("&c[祈愿] 今日次数已用尽")); return; }
            count = Math.min(count, pool.maxDaily - used);
        }
        if (count <= 0) return;

        int cost = pool.costAmount * count;
        if (!economy.charge(p, pool.costMode, pool.costItem, cost)) {
            p.sendMessage(C.color("&c[祈愿] " + pool.costName + "不足，需要 " + cost));
            return;
        }

        drawing.add(p.getUniqueId());
        try {
            List<DrawResult> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                engine.tickRules(st, pool);       // 所有规则 combos+1
                DrawResult r = engine.draw(st, pool);
                if (r == null) continue;
                fillLevelDisplay(r);
                results.add(r);
                giveRewards(p, r);
                st.addRecord("[" + pool.name.replaceAll("[§&][0-9a-fk-or]", "") + "] " + r.level + "星 " +
                        (r.reward == null ? "?" : r.reward.name) + (r.hitAim ? " [定轨]" : ""));
                checkBroadcast(p, r);
            }
            st.dailyCount.put(pool.key, used + count);
            saveState(p);

            if (bedrockClient || bedrock.isBedrock(p)) {
                bedrockUI.showResult(p, pool.key, results);
            } else {
                JavaGUI g = new JavaGUI(this, p, JavaGUI.Kind.RESULT);
                g.poolKey = pool.key;
                g.lastResults = results;
                g.open();
            }
        } finally {
            drawing.remove(p.getUniqueId());
        }
    }

    private void fillLevelDisplay(DrawResult r) {
        LevelDef l = getLevel(r.level);
        if (l != null) { r.levelName = l.name; r.levelColor = l.color; }
    }

    /** 发奖励 */
    private void giveRewards(Player p, DrawResult r) {
        if (r.reward == null) return;
        for (Reward.ItemReward it : r.reward.items) {
            try {
                if ("command".equalsIgnoreCase(it.mode)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it.command.replace("${player}", p.getName()));
                } else if ("nbt".equalsIgnoreCase(it.mode)) {
                    giveNbt(p, it);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "minecraft:give " + p.getName() + " " + it.type + " " + it.count);
                }
            } catch (Throwable t) { getLogger().warning("[LuckyPray] 发放奖励失败: " + t); }
        }
        for (String cmd : r.reward.commands)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("${player}", p.getName()));
    }

    /**
     * NBT/组件物品发放（1.20.5+ 数据组件语法，零依赖）。
     * snbt 字段写组件串，可带 [ ] 也可不带，如：
     *   snbt: "custom_name={\"color\":\"blue\",\"text\":\"星辰之剑\"},enchantments={levels:{\"minecraft:sharpness\":5}}"
     * 拼接为: minecraft:give <player> <type>[<snbt>] <count>
     */
    private void giveNbt(Player p, Reward.ItemReward it) {
        String comp = it.nbt == null ? "" : it.nbt.trim();
        String spec;
        if (comp.isEmpty()) {
            spec = it.type;
        } else if (comp.startsWith("[")) {
            spec = it.type + comp;
        } else {
            spec = it.type + "[" + comp + "]";
        }
        String cmd = "minecraft:give " + p.getName() + " " + spec + " " + Math.max(1, it.count);
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        if (!ok) {
            getLogger().warning("[LuckyPray] NBT 物品发放失败（检查组件语法）: " + cmd);
            p.sendMessage(C.color("&c[祈愿] 物品发放出错，请联系管理员查看后台日志"));
        }
    }

    private void checkBroadcast(Player p, DrawResult r) {
        if (!r.level.equals(broadcastLevel)) return;
        LevelDef l = getLevel(r.level);
        String name = r.reward == null ? "?" : r.reward.name;
        Bukkit.broadcastMessage(C.color("&6[祈愿] &f" + p.getName() + " &c祈愿出了 " +
                (l == null ? r.level + "星" : l.name) + " &f" + name + " !"));
    }

    /** 帮助/说明 */
    public String[] helpMessages(String poolKey) {
        Pool p = getPool(poolKey);
        if (p == null) return new String[]{"&c[祈愿] 卡池不存在"};
        List<String> out = new ArrayList<>();
        out.add("&d&l=== " + p.name + " 说明 ===");
        for (Map.Entry<String, LevelDef> e : levels.entrySet())
            out.add("&7" + e.getValue().name + " &f概率 &6" + e.getValue().probability + "%");
        for (PityRule rule : p.rules)
            out.add("&b" + rule.name + "&7: " + rule.combos + "抽保底 " + rule.level + "星, " +
                    rule.threshold + "抽起概率递增, 定轨上限 " + rule.maxOrbitScore);
        out.add("&7每日上限 &6" + (p.unlimitedDaily() ? "无限" : p.maxDaily));
        return out.stream().map(C::color).toArray(String[]::new);
    }

    // ===== 事件 =====
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null) return;
        String type = it.getType().name();
        if (type.equalsIgnoreCase(openItem.replace("minecraft:", "")) || openItem.equalsIgnoreCase(type)) {
            if (e.getAction().name().contains("RIGHT")) {
                openMenu(e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof JavaGUI)) return;
        e.setCancelled(true);
        JavaGUI gui = (JavaGUI) e.getInventory().getHolder();
        if (gui.player != e.getWhoClicked()) return;
        gui.onClick(e);
    }

    // ===== 命令 =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用"); return true; }
            openMenu((Player) sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("luckypray.admin")) { sender.sendMessage("无权限"); return true; }
                reloadConfig(); loadConfigs();
                sender.sendMessage("§a[LuckyPray] 配置已重载");
                return true;
            case "getitem":
                if (!sender.hasPermission("luckypray.admin") || !(sender instanceof Player)) return true;
                // 简化：给 diamond
                ((Player) sender).getInventory().addItem(new ItemStack(org.bukkit.Material.DIAMOND, 1));
                return true;
            case "pray":
                // /pray pray <player> <pool> <count>
                if (!sender.hasPermission("luckypray.admin")) { sender.sendMessage("无权限"); return true; }
                if (args.length < 4) { sender.sendMessage("用法: /pray pray <玩家> <卡池key> <次数>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                Pool pool = getPool(args[2]);
                int n; try { n = Integer.parseInt(args[3]); } catch (Exception ex) { return true; }
                if (target == null || pool == null) { sender.sendMessage("玩家或卡池不存在"); return true; }
                doDraw(target, pool, n, false);
                return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) out.addAll(Arrays.asList("menu", "reload", "pray", "getitem"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("pray"))
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (args.length == 3 && args[0].equalsIgnoreCase("pray")) out.addAll(pools.keySet());
        return out;
    }
}
