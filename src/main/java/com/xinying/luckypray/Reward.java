package com.xinying.luckypray;

import java.util.*;

/** 单个奖励条目 */
public class Reward {
    public String index = "";       // 唯一识别名
    public String name = "";        // 显示名
    public String description = ""; // 描述
    public double probability = 1;  // 同等级内的权重
    public List<String> commands = new ArrayList<>(); // 发放时执行的命令，${player} 占位
    public List<ItemReward> items = new ArrayList<>();

    public static Reward fromMap(Map<?, ?> m) {
        Reward r = new Reward();
        if (m == null) return r;
        r.index = Yaml.S(m.get("index"), r.index);
        r.name = Yaml.S(m.get("name"), r.name);
        r.description = Yaml.S(m.get("description"), Yaml.S(m.get("discription"), r.description));
        r.probability = Yaml.N(m.get("probability"), r.probability);
        List<?> cmds = Yaml.asList(m.get("cmds"));
        if (cmds != null) for (Object c : cmds) r.commands.add(String.valueOf(c));
        List<?> items = Yaml.asList(m.get("rewardItems"));
        if (items != null) for (Object it : items) {
            Map<?, ?> im = Yaml.asMap(it);
            if (im != null) r.items.add(ItemReward.fromMap(im));
        }
        return r;
    }

    /** 一个具体的物品奖励，支持 mode: item / nbt / command */
    public static class ItemReward {
        public String mode = "item";   // item | nbt | command
        public String name = "";
        public String type = "minecraft:diamond";
        public int count = 1;
        public String nbt = "";        // mode=nbt 时的 SNBT/JSON
        public String command = "";    // mode=command 时执行的命令

        public static ItemReward fromMap(Map<?, ?> m) {
            ItemReward i = new ItemReward();
            i.mode = Yaml.S(m.get("mode"), i.mode);
            i.name = Yaml.S(m.get("name"), i.name);
            i.type = Yaml.S(m.get("type"), i.type);
            i.count = (int) Yaml.N(m.get("count"), i.count);
            i.nbt = Yaml.S(m.get("snbt"), Yaml.S(m.get("nbt"), i.nbt));
            i.command = Yaml.S(m.get("command"), i.command);
            return i;
        }
    }

    static String S(Object o, String d) { return o == null ? d : String.valueOf(o); }
    static double N(Object o, double d) {
        if (o == null) return d;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return d; }
    }
}
