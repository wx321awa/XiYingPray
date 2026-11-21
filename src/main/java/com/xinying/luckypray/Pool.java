package com.xinying.luckypray;

import java.util.*;

/** 卡池定义 */
public class Pool {
    public String key = "";                 // 配置里的唯一 key
    public String name = "&c&l祈愿池";       // 显示名
    public String icon = "NETHER_STAR";     // Java 物品材质名（用作图标）
    public boolean enabled = true;
    public String description = "";
    public String endDate = "2099-12-31-0-0-0";
    public int maxDaily = 60;
    public int resetHour = 4;
    public int resetMinute = 0;
    public boolean dailyReset = true;

    // 祈愿方式：显示名 -> 抽数
    public LinkedHashMap<String, Integer> prayModes = new LinkedHashMap<>();

    // 消耗：mode = vault | points | item | free
    public String costMode = "vault";
    public String costItem = "minecraft:diamond";
    public int costAmount = 1;
    public String costName = "钻石";

    public List<PityRule> rules = new ArrayList<>();
    // 等级 -> 奖品列表
    public LinkedHashMap<String, List<Reward>> rewards = new LinkedHashMap<>();

    public static Pool fromMap(String key, Map<?, ?> m) {
        Pool p = new Pool();
        p.key = key;
        if (m == null) return p;
        p.name = Yaml.S(m.get("name"), p.name);
        p.icon = Yaml.S(m.get("icon"), p.icon);
        p.enabled = Yaml.B(m.get("enabled"), p.enabled);
        p.description = Yaml.S(m.get("discription"), Yaml.S(m.get("description"), p.description));
        p.endDate = Yaml.S(m.get("diedLine"), Yaml.S(m.get("endDate"), p.endDate));
        p.maxDaily = (int) Yaml.N(m.get("maxPrayCountPerday"), p.maxDaily);
        p.resetHour = (int) Yaml.N(m.get("resetHour"), p.resetHour);
        p.resetMinute = (int) Yaml.N(m.get("resetMinute"), p.resetMinute);
        p.dailyReset = Yaml.B(m.get("dailyReset"), p.dailyReset);

        Map<?, ?> modes = Yaml.asMap(m.get("prayMode"));
        if (modes != null) for (Map.Entry<?, ?> e : modes.entrySet())
            p.prayModes.put(String.valueOf(e.getKey()), (int) Yaml.N(e.getValue(), 1));
        if (p.prayModes.isEmpty()) { p.prayModes.put("&l单抽", 1); p.prayModes.put("&l十连", 10); }

        Map<?, ?> pm = Yaml.asMap(m.get("prayItem"));
        if (pm != null) {
            p.costMode = Yaml.S(pm.get("mode"), p.costMode); // vault/points/item/free
            p.costItem = Yaml.S(pm.get("type"), p.costItem);
            p.costAmount = (int) Yaml.N(pm.get("count"), p.costAmount);
            p.costName = Yaml.S(pm.get("name"), p.costName);
        }

        List<?> rs = Yaml.asList(m.get("regulars"));
        if (rs != null) for (Object r : rs) {
            Map<?, ?> rm = Yaml.asMap(r);
            if (rm != null) p.rules.add(PityRule.fromMap(rm));
        }

        Map<?, ?> rew = Yaml.asMap(m.get("rewards"));
        if (rew != null) {
            for (Map.Entry<?, ?> e : rew.entrySet()) {
                String lv = String.valueOf(e.getKey());
                List<Reward> list = new ArrayList<>();
                List<?> rl = Yaml.asList(e.getValue());
                if (rl != null) for (Object r : rl) {
                    Map<?, ?> rm = Yaml.asMap(r);
                    if (rm != null) list.add(Reward.fromMap(rm));
                }
                p.rewards.put(lv, list);
            }
        }
        return p;
    }

    public List<Reward> rewardsOf(String level) {
        List<Reward> r = rewards.get(level);
        return r == null ? new ArrayList<>() : r;
    }

    /** 是否无每日上限：maxPrayCountPerday <= 0 或 dailyReset: false */
    public boolean unlimitedDaily() { return maxDaily <= 0 || !dailyReset; }

    /** 是否已过截止日期（diedLine 格式 yyyy-MM-dd-H-m-s，解析失败视为永不过期） */
    public boolean expired() {
        try {
            String[] parts = endDate.split("-");
            if (parts.length < 3) return false;
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]),
                    parts.length > 3 ? Integer.parseInt(parts[3]) : 0,
                    parts.length > 4 ? Integer.parseInt(parts[4]) : 0,
                    parts.length > 5 ? Integer.parseInt(parts[5]) : 0);
            return System.currentTimeMillis() > c.getTimeInMillis();
        } catch (Exception e) { return false; }
    }
    public Reward findReward(String level, String index) {
        for (Reward r : rewardsOf(level)) if (r.index.equals(index)) return r;
        return null;
    }

    static String S(Object o, String d) { return o == null ? d : String.valueOf(o); }
    static double N(Object o, double d) {
        if (o == null) return d;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return d; }
    }
    static boolean B(Object o, boolean d) {
        if (o == null) return d;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(String.valueOf(o));
    }
}
