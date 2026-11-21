package com.xinying.luckypray;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** YAML 节点 -> Map 的兼容层（Bukkit 的 MemorySection 在新版不一定是 java.util.Map） */
public final class Yaml {
    private Yaml() {}
    @SuppressWarnings("unchecked")
    public static Map<?, ?> asMap(Object o) {
        if (o == null) return null;
        if (o instanceof Map) return (Map<?, ?>) o;
        if (o instanceof ConfigurationSection) return ((ConfigurationSection) o).getValues(false);
        return null;
    }
    public static List<?> asList(Object o) {
        if (o instanceof List) return (List<?>) o;
        return null;
    }
    public static String S(Object o, String d) { return o == null ? d : String.valueOf(o); }
    public static double N(Object o, double d) {
        if (o == null) return d;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return d; }
    }
    public static boolean B(Object o, boolean d) {
        if (o == null) return d;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(String.valueOf(o));
    }
}
