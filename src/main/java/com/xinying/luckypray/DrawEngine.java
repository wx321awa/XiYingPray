package com.xinying.luckypray;

import java.util.*;

/**
 * 抽奖引擎核心算法。
 * 多保底规则：按 combos 从高到低尝试；每个规则若达到 threshold 则重算等级概率；
 * 若抽出该规则的保底等级则作数，否则继续尝试下一个规则。
 */
public class DrawEngine {
    private final LuckyPray plugin;
    private static final Random random = new Random();

    public DrawEngine(LuckyPray plugin) { this.plugin = plugin; }

    /** 按权重随机选一个对象 */
    public static <T> T pick(List<T> list, java.util.function.ToDoubleFunction<T> weight) {
        if (list == null || list.isEmpty()) return null;
        double sum = 0;
        for (T t : list) sum += Math.max(0, weight.applyAsDouble(t));
        if (sum <= 0) return list.get(randomInt(list.size()));
        double roll = Math.random() * sum;
        double acc = 0;
        for (T t : list) {
            acc += Math.max(0, weight.applyAsDouble(t));
            if (roll <= acc) return t;
        }
        return list.get(list.size() - 1);
    }

    /** 调高某等级的概率（原神式软保底），其余等级按比例压缩 */
    public static List<LevelDef> boostLevel(List<LevelDef> levels, String levelIndex, double plus) {
        double sum = 0;
        for (LevelDef l : levels) sum += l.probability;
        if (sum <= 0) return new ArrayList<>(levels);
        List<LevelDef> out = new ArrayList<>();
        double targetOld = 0, targetNew = 0;
        for (LevelDef l : levels) if (l.index.equals(levelIndex)) targetOld = l.probability / sum;
        double ratio = 1;
        if (targetOld + plus < 1) {
            targetNew = sum * (targetOld + plus);
            double others = sum - targetOld * sum; // 原来的其他总权重
            double newOthers = sum - targetNew;
            ratio = others > 0 ? newOthers / others : 0;
        } else {
            targetNew = sum;
            ratio = 0;
        }
        for (LevelDef l : levels) {
            LevelDef c = new LevelDef();
            c.index = l.index; c.name = l.name; c.color = l.color; c.sound = l.sound;
            c.probability = l.index.equals(levelIndex) ? targetNew : l.probability * ratio;
            out.add(c);
        }
        return out;
    }

    /** 按玩家数据抽一次 */
    public DrawResult draw(PlayerState st, Pool pool) {
        List<LevelDef> baseLevels = new ArrayList<>();
        for (String lv : pool.rewards.keySet()) {
            LevelDef def = plugin.getLevel(lv);
            if (def != null) baseLevels.add(def);
        }
        if (baseLevels.isEmpty()) return null;

        // 先按基础概率抽等级
        List<LevelDef> levels = new ArrayList<>(baseLevels);
        String rewardLevel = pick(levels, l -> l.probability).index;

        // 按 combos 从高到低尝试保底规则
        List<PityRule> rules = new ArrayList<>(pool.rules);
        rules.sort((a, b) -> Integer.compare(b.combos, a.combos));
        DrawResult result = null;

        for (PityRule rule : rules) {
            PlayerState.RuleState rs = st.rule(pool.key, rule.name);
            // tickRules 已在本抽开始时 combos+1，这里直接取当前值即为本抽序号
            int next = rs.combos;

            // 软保底递增
            if (next >= rule.threshold) {
                double plus;
                if (next >= rule.combos) plus = 1.0;
                else plus = (double) (next - rule.threshold + 1) / (double) Math.max(1, rule.combos - rule.threshold);
                levels = boostLevel(baseLevels, rule.level, plus);
                rewardLevel = pick(levels, l -> l.probability).index;
            }

            if (rewardLevel.equals(rule.level)) {
                // 命中保底等级：处理定轨
                List<String> aim = new ArrayList<>(rs.aimRewards);
                if (aim.isEmpty() && !rule.aimRewards.isEmpty()) aim.addAll(rule.aimRewards);

                boolean fullOrbit = rs.orbitScore >= rule.maxOrbitScore;
                Reward chosen;
                boolean hit = false;
                if (fullOrbit && !aim.isEmpty()) {
                    // 满定轨：必出定轨奖品
                    List<Reward> aimRewards = new ArrayList<>();
                    for (String idx : aim) {
                        Reward r = pool.findReward(rewardLevel, idx);
                        if (r != null) aimRewards.add(r);
                    }
                    chosen = pick(aimRewards, r -> r.probability);
                    hit = true;
                } else {
                    // 未满足定轨：本等级随机（"歪"）
                    chosen = pick(pool.rewardsOf(rewardLevel), r -> r.probability);
                    // 歪了之后累计 orbitScore；判断是否命中定轨奖品
                    if (!rule.aimRewards.isEmpty() || !aim.isEmpty()) {
                        for (String idx : aim) if (chosen != null && chosen.index.equals(idx)) hit = true;
                    }
                }

                // 更新规则状态
                rs.combos = 0;
                if (rule.maxOrbitScore > 0) {
                    if (hit) rs.orbitScore = 0;
                    else rs.orbitScore = Math.min(rule.maxOrbitScore, rs.orbitScore + 1);
                }
                result = new DrawResult(pool.key, rewardLevel, chosen);
                result.ruleName = rule.name;
                result.hitAim = hit;
                break;
            }
        }

        if (result == null) {
            // 没有触发任何保底等级：普通掉落
            Reward chosen = pick(pool.rewardsOf(rewardLevel), r -> r.probability);
            result = new DrawResult(pool.key, rewardLevel, chosen);
        }

        // 所有规则的组合数 +1（即使本次被高优先级规则消费了，也要给低优先级规则累计）
        for (PityRule rule : pool.rules) {
            PlayerState.RuleState rs = st.rule(pool.key, rule.name);
            // 命中的规则上面已经归零，这里只累加未消费的
        }
        return result;
    }

    /** 在 draw 之前调用：给所有规则 combos+1 */
    public void tickRules(PlayerState st, Pool pool) {
        for (PityRule rule : pool.rules) {
            PlayerState.RuleState rs = st.rule(pool.key, rule.name);
            rs.combos++;
        }
    }

    private static int randomInt(int bound) { return bound <= 1 ? 0 : random.nextInt(bound); }
}
