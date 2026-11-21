package com.xinying.luckypray;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** 玩家持久状态：保底计数、每日次数、定轨、记录 */
public class PlayerState {
    public String name = "";
    public String uuid = "";

    // poolKey -> daily count
    public Map<String, Integer> dailyCount = new HashMap<>();
    public String dailyDate = "";

    // poolKey -> 上次重置水位线（epoch millis，用于按池自定义重置时间）
    public Map<String, Long> lastReset = new HashMap<>();

    // poolKey|ruleName -> RuleState
    public Map<String, RuleState> rules = new HashMap<>();

    // 最近记录
    public List<String> records = new ArrayList<>();

    public static class RuleState {
        public int combos = 0;
        public int orbitScore = 0;
        public List<String> aimRewards = new ArrayList<>();
    }

    public RuleState rule(String poolKey, String ruleName) {
        return rules.computeIfAbsent(poolKey + "|" + ruleName, k -> new RuleState());
    }

    public static PlayerState load(File f) {
        PlayerState s = new PlayerState();
        if (!f.exists()) return s;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        s.name = y.getString("name", "");
        s.uuid = y.getString("uuid", "");
        s.dailyDate = y.getString("dailyDate", "");
        for (String k : y.getConfigurationSection("dailyCount") == null ? new HashSet<String>() : y.getConfigurationSection("dailyCount").getKeys(false))
            s.dailyCount.put(k, y.getInt("dailyCount." + k));
        ConfigurationSection lr = y.getConfigurationSection("lastReset");
        if (lr != null) for (String k : lr.getKeys(false)) s.lastReset.put(k, lr.getLong(k));
        ConfigurationSection rs = y.getConfigurationSection("rules");
        if (rs != null) for (String k : rs.getKeys(false)) {
            RuleState r = new RuleState();
            r.combos = rs.getInt(k + ".combos");
            r.orbitScore = rs.getInt(k + ".orbitScore");
            r.aimRewards = rs.getStringList(k + ".aimRewards");
            s.rules.put(k, r);
        }
        s.records = y.getStringList("records");
        return s;
    }

    public void save(File f) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("uuid", uuid);
        y.set("dailyDate", dailyDate);
        for (Map.Entry<String, Integer> e : dailyCount.entrySet()) y.set("dailyCount." + e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : lastReset.entrySet()) y.set("lastReset." + e.getKey(), e.getValue());
        for (Map.Entry<String, RuleState> e : rules.entrySet()) {
            String pre = "rules." + e.getKey();
            y.set(pre + ".combos", e.getValue().combos);
            y.set(pre + ".orbitScore", e.getValue().orbitScore);
            y.set(pre + ".aimRewards", e.getValue().aimRewards);
        }
        y.set("records", records);
        try { y.save(f); } catch (IOException ignored) {}
    }

    public void addRecord(String line) {
        records.add(0, line);
        while (records.size() > 60) records.remove(records.size() - 1);
    }
}
