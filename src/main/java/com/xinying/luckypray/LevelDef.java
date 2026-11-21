package com.xinying.luckypray;

import java.util.*;

/** 一个奖励等级（1~5星）的全局定义 */
public class LevelDef {
    public String index = "1";
    public String name = "✿";
    public String color = "&f";
    public String sound = "";
    public double probability = 100; // 基础权重（建议总和100）

    public static LevelDef fromMap(Map<?, ?> m) {
        LevelDef l = new LevelDef();
        if (m == null) return l;
        l.index = str(m.get("index"), l.index);
        l.name = str(m.get("name"), l.name);
        l.color = str(m.get("color"), l.color);
        l.sound = str(m.get("sound"), l.sound);
        l.probability = num(m.get("probability"), l.probability);
        return l;
    }
    static String str(Object o, String def) { return o == null ? def : String.valueOf(o); }
    static double num(Object o, double def) {
        if (o == null) return def;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
