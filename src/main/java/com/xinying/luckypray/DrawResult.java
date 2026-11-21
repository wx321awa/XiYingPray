package com.xinying.luckypray;

/** 单次抽奖结果 */
public class DrawResult {
    public String poolKey;
    public String level;
    public Reward reward;
    public String ruleName = "";   // 触发的保底规则
    public boolean hitAim = false; // 是否命中定轨
    public String levelName = "";
    public String levelColor = "";

    public DrawResult(String poolKey, String level, Reward reward) {
        this.poolKey = poolKey;
        this.level = level;
        this.reward = reward;
    }
}
