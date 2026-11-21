package com.xinying.luckypray;

/** 颜色代码工具：支持 & 和 § */
public final class C {
    private C() {}
    public static String color(String s) {
        if (s == null) return "";
        return s.replace('&', '§');
    }
    public static String plain(String s) {
        if (s == null) return "";
        return s.replaceAll("[§&][0-9a-fk-or]", "");
    }
    public static org.bukkit.Material material(String name, org.bukkit.Material def) {
        if (name == null) return def;
        String s = name.replace("minecraft:", "").replace("textures/items/", "").toUpperCase();
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(s);
        return m == null ? def : m;
    }
}
