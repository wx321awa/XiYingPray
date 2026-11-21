package com.xinying.luckypray;

import java.util.*;

/** 保底/十连规则：软保底递增 + 定轨 */
public class PityRule {
    public String name = "保底";
    public int combos = 60;          // 硬保底抽数
    public String level = "5";       // 保底等级
    public int threshold = 45;       // 概率递增起始抽数
    public int maxOrbitScore = 1;    // 0=不会歪；1=最多歪一次；2=最多歪两次
    public boolean customAimRewards = false; // 是否让玩家自选定轨
    public int aimRewardsNum = 1;    // 定轨物品数量
    public List<String> aimRewards = new ArrayList<>(); // 满定轨奖品 index 列表

    public static PityRule fromMap(Map<?, ?> m) {
        PityRule p = new PityRule();
        if (m == null) return p;
        p.name = Yaml.S(m.get("name"), p.name);
        p.combos = (int) Yaml.N(m.get("combos"), p.combos);
        p.level = Yaml.S(m.get("level"), p.level);
        p.threshold = (int) Yaml.N(m.get("threshold"), p.threshold);
        p.maxOrbitScore = (int) Yaml.N(m.get("maxOrbitScore"), p.maxOrbitScore);
        p.customAimRewards = Yaml.B(m.get("customAimRewards"), p.customAimRewards);
        p.aimRewardsNum = (int) Yaml.N(m.get("aimRewardsNum"), p.aimRewardsNum);
        List<?> ar = Yaml.asList(m.get("aimRewards"));
        if (ar != null) for (Object a : ar) p.aimRewards.add(String.valueOf(a));
        return p;
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
