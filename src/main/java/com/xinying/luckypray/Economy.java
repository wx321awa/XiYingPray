package com.xinying.luckypray;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.inventory.ItemStack;

/** 经济对接：Vault 经济 / PlayerPoints 点券 / 物品。用反射避免硬依赖导致 NoClassDefFoundError。 */
public class Economy {

    private Object vaultEconomy = null;      // net.milkbowl.vault.economy.Economy
    private Object playerPointsApi = null;   // org.black_ixx.playerpoints.PlayerPointsAPI

    public void init() {
        // Vault（软依赖，通过 Bukkit ServicesManager）
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) econClass);
            if (rsp != null) vaultEconomy = rsp.getProvider();
        } catch (Throwable ignored) {}

        // PlayerPoints
        try {
            Class<?> pp = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object inst = pp.getMethod("getInstance").invoke(null);
            if (inst != null) playerPointsApi = inst.getClass().getMethod("getAPI").invoke(inst);
        } catch (Throwable ignored) {}
    }

    public boolean hasVault() { return vaultEconomy != null; }
    public boolean hasPoints() { return playerPointsApi != null; }

    /** 检查/扣除。mode: vault | points | item | free */
    public boolean charge(Player p, String mode, String itemType, int amount) {
        if (amount <= 0 || "free".equalsIgnoreCase(mode)) return true;
        try {
            if ("vault".equalsIgnoreCase(mode)) {
                if (vaultEconomy == null) return false;
                Object provider = vaultEconomy;
                Class<?> ec = Class.forName("net.milkbowl.vault.economy.Economy");
                Double bal = (Double) ec.getMethod("getBalance", String.class).invoke(provider, p.getName());
                if (bal == null || bal < amount) return false;
                Object res = ec.getMethod("withdrawPlayer", String.class, double.class).invoke(provider, p.getName(), (double) amount);
                return res != null && Boolean.TRUE.equals(invokeSuccess(res));
            }
            if ("points".equalsIgnoreCase(mode)) {
                if (playerPointsApi == null) return false;
                Class<?> api = Class.forName("org.black_ixx.playerpoints.PlayerPointsAPI");
                java.util.UUID uuid = p.getUniqueId();
                Integer has = (Integer) api.getMethod("look", java.util.UUID.class).invoke(playerPointsApi, uuid);
                if (has == null || has < amount) return false;
                Object res = api.getMethod("take", java.util.UUID.class, int.class).invoke(playerPointsApi, uuid, amount);
                return res == null || Boolean.TRUE.equals(res); // take 返回 boolean
            }
            if ("item".equalsIgnoreCase(mode)) {
                return takeItems(p, itemType, amount);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[LuckyPray] charge error: " + t);
            return false;
        }
        return false;
    }

    /** 查询余额描述（给 GUI 显示） */
    public String balanceText(Player p, String mode, String itemType) {
        try {
            if ("vault".equalsIgnoreCase(mode) && vaultEconomy != null) {
                Class<?> ec = Class.forName("net.milkbowl.vault.economy.Economy");
                Double bal = (Double) ec.getMethod("getBalance", String.class).invoke(vaultEconomy, p.getName());
                return String.format("%.2f", bal == null ? 0 : bal);
            }
            if ("points".equalsIgnoreCase(mode) && playerPointsApi != null) {
                Class<?> api = Class.forName("org.black_ixx.playerpoints.PlayerPointsAPI");
                Integer has = (Integer) api.getMethod("look", java.util.UUID.class).invoke(playerPointsApi, p.getUniqueId());
                return String.valueOf(has == null ? 0 : has);
            }
            if ("item".equalsIgnoreCase(mode)) {
                return String.valueOf(countItems(p, itemType));
            }
        } catch (Throwable ignored) {}
        return "?";
    }

    private Object invokeSuccess(Object res) throws Exception {
        try { return res.getClass().getMethod("transactionSuccess").invoke(res); }
        catch (Throwable t) { return true; }
    }

    @SuppressWarnings("deprecation")
    private boolean takeItems(Player p, String type, int amount) {
        org.bukkit.Material mat = matchMaterial(type);
        if (mat == null) return false;
        int need = amount;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() != mat) continue;
            int take = Math.min(need, it.getAmount());
            it.setAmount(it.getAmount() - take);
            need -= take;
            if (need <= 0) break;
        }
        return need <= 0;
    }

    private int countItems(Player p, String type) {
        org.bukkit.Material mat = matchMaterial(type);
        if (mat == null) return 0;
        int n = 0;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType() == mat) n += it.getAmount();
        return n;
    }

    private org.bukkit.Material matchMaterial(String type) {
        if (type == null) return null;
        String s = type.replace("minecraft:", "").toUpperCase();
        return org.bukkit.Material.matchMaterial(s);
    }
}
