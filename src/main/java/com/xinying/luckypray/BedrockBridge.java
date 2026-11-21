package com.xinying.luckypray;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Geyser / Floodgate 桥接（全反射，无硬依赖）。
 * 关键设计：Cumulus 表单类一律从 Geyser 实例自己的类加载器解析，
 * 避免与 floodgate 内置的旧版 cumulus 发生类身份冲突。
 */
public class BedrockBridge {
    private final LuckyPray plugin;
    private Method geyserApi = null;
    private Method floodgateIsBedrock = null;
    private boolean geyserOk = false, floodgateOk = false, formOk = false;

    // 发送 + 构建表单用的反射句柄（全部来自 Geyser 的类加载器）
    private List<Method> sendFormCandidates = new ArrayList<>();
    private Class<?> simpleFormCls = null;
    private Method titleM, contentM, buttonM, validHandlerM, buildMethod;

    public BedrockBridge(LuckyPray plugin) { this.plugin = plugin; }

    public void init() {
        // GeyserApi.api()
        Object apiObj = null;
        try {
            Class<?> apiCls = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserApi = apiCls.getMethod("api");
            apiObj = geyserApi.invoke(null);
            geyserOk = apiObj != null;
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] Geyser API 初始化失败：" + t);
        }

        // Floodgate 判断基岩
        try {
            Class<?> fg = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object inst = fg.getMethod("getInstance").invoke(null);
            floodgateIsBedrock = inst.getClass().getMethod("isFloodgatePlayer", UUID.class);
            floodgateOk = true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] Floodgate API 初始化失败：" + t.getMessage());
        }

        if (!geyserOk) return;

        // 收集 apiObj 上所有 sendForm(UUID, X) 方法
        try {
            for (Method m : apiObj.getClass().getMethods()) {
                if (!m.getName().equals("sendForm")) continue;
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 2 && ps[0] == UUID.class) {
                    sendFormCandidates.add(m);
                    plugin.getLogger().info("[LuckyPray] sendForm 候选: (UUID, " + ps[1].getName()
                            + ") @loader=" + Integer.toHexString(ps[1].getClassLoader().hashCode()));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] 收集 sendForm 方法失败：" + t);
        }

        // 从 sendForm 参数类（Geyser 自己的 cumulus Form）的类加载器解析 SimpleForm
        try {
            Class<?> geyserFormCls = null;
            for (Method m : sendFormCandidates) {
                Class<?> p = m.getParameterTypes()[1];
                if (p.getName().endsWith(".form.Form")) { geyserFormCls = p; break; }
            }
            if (geyserFormCls == null) {
                plugin.getLogger().warning("[LuckyPray] 未在 Geyser API 中找到 Form 参数类");
                return;
            }
            ClassLoader gcl = geyserFormCls.getClassLoader();
            simpleFormCls = gcl.loadClass("org.geysermc.cumulus.form.SimpleForm");

            Class<?> builderCls = simpleFormCls.getMethod("builder").invoke(null).getClass();
            titleM = builderCls.getMethod("title", String.class);
            contentM = builderCls.getMethod("content", String.class);
            buttonM = builderCls.getMethod("button", String.class);
            buildMethod = builderCls.getMethod("build");
            for (Method m : builderCls.getMethods()) {
                if (m.getName().equals("validResultHandler") && m.getParameterCount() == 1
                        && BiConsumer.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    validHandlerM = m; break;
                }
            }
            formOk = titleM != null && contentM != null && buttonM != null
                    && validHandlerM != null && buildMethod != null;
            plugin.getLogger().info("[LuckyPray] 表单构建 " + (formOk ? "就绪（使用 Geyser 自带 Cumulus）"
                    : "失败：builder 方法不全"));
            if (!formOk) {
                plugin.getLogger().warning("[LuckyPray] builder 类: " + builderCls.getName()
                        + " title=" + titleM + " content=" + contentM + " button=" + buttonM
                        + " handler=" + validHandlerM + " build=" + buildMethod);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] Cumulus 表单初始化失败：" + t);
        }
    }

    public boolean isBedrock(Player p) {
        if (floodgateOk && floodgateIsBedrock != null) {
            try {
                Class<?> fg = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object inst = fg.getMethod("getInstance").invoke(null);
                Object r = floodgateIsBedrock.invoke(inst, p.getUniqueId());
                if (r instanceof Boolean) return (Boolean) r;
            } catch (Throwable t) {
                plugin.getLogger().warning("[LuckyPray] isBedrock floodgate 调用失败：" + t);
            }
        }
        // 兜底：Geyser 的 isBedrockPlayer
        if (geyserOk) {
            try {
                Object apiObj = geyserApi.invoke(null);
                for (Method m : apiObj.getClass().getMethods()) {
                    if (m.getName().equals("isBedrockPlayer") && m.getParameterCount() == 1) {
                        Object r = m.invoke(apiObj, p.getUniqueId());
                        if (r instanceof Boolean) return (Boolean) r;
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[LuckyPray] isBedrock geyser 调用失败：" + t);
            }
        }
        return false;
    }

    /** 构建并给基岩玩家发送 SimpleForm。handler 收到 (form, response)，用 clickedButton(response) 取按钮序号 */
    public boolean openSimpleForm(Player p, String title, String content,
                                  List<String> buttons, BiConsumer<Object, Object> handler) {
        if (!formOk) {
            plugin.getLogger().warning("[LuckyPray] 表单未就绪，无法发给 " + p.getName());
            return false;
        }
        try {
            Object b = simpleFormCls.getMethod("builder").invoke(null);
            titleM.invoke(b, title == null ? "" : title);
            contentM.invoke(b, content == null ? "" : content);
            if (buttons != null) for (String s : buttons) buttonM.invoke(b, s == null ? "" : s);
            // Geyser 回调跑在 Geyser 线程：统一调度回主线程，避免异步 dispatch 命令/Bukkit API 被拒
            if (handler != null) {
                BiConsumer<Object, Object> safe = (form, res) ->
                        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(form, res));
                validHandlerM.invoke(b, safe);
            }
            Object form = buildMethod.invoke(b);
            return sendForm(p, form);
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] 构建表单失败(" + p.getName() + "): " + t);
            return false;
        }
    }

    /** 从响应对象取被点击按钮序号 */
    public int clickedButton(Object response) {
        try {
            Object r = response.getClass().getMethod("clickedButtonId").invoke(response);
            return r instanceof Number ? ((Number) r).intValue() : -1;
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] clickedButtonId 失败: " + t);
            return -1;
        }
    }

    /** 直接发送已构建好的 Form（保留旧接口） */
    public boolean sendForm(Player p, Object form) {
        if (form == null) { plugin.getLogger().warning("[LuckyPray] sendForm: form 为 null"); return false; }
        if (!geyserOk) { plugin.getLogger().warning("[LuckyPray] sendForm: Geyser API 未就绪"); return false; }
        try {
            Object apiObj = geyserApi.invoke(null);
            for (Method m : sendFormCandidates) {
                Class<?> paramCls = m.getParameterTypes()[1];
                if (paramCls.isInstance(form)) {
                    Object r = m.invoke(apiObj, p.getUniqueId(), form);
                    boolean ok = (r instanceof Boolean) ? (Boolean) r : true;
                    if (!ok) plugin.getLogger().warning("[LuckyPray] sendForm(" + p.getName()
                            + ") Geyser 返回 false（玩家可能已离线）");
                    return ok;
                }
            }
            plugin.getLogger().warning("[LuckyPray] sendForm(" + p.getName() + "): 表单类不匹配! form="
                    + form.getClass().getName() + " @loader=" + Integer.toHexString(form.getClass().getClassLoader().hashCode()));
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("[LuckyPray] sendForm(" + p.getName() + ") 异常: " + t);
            return false;
        }
    }

    public boolean ready() { return geyserOk; }
    public boolean formReady() { return formOk; }
}
