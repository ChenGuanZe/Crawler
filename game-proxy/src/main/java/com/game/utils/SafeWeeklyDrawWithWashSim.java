package com.game.utils;

import java.util.Random;

public class SafeWeeklyDrawWithWashSim {

    /**
     * ************ 优化后的开奖算法（压低高倍率） ************
     *
     * 倍率越高 → 概率越低（指数压制）
     * 下注越多 → 高倍权重再降低（自然风险控制）
     */
    public static int drawNumberSafe(
            double[] bets,
            double[] multipliers,
            Random rand
    ) {
        int n = bets.length;
        double[] weights = new double[n];
        double sum = 0;

        for (int i = 0; i < n; i++) {

            // 倍率越高，权重越低（1 / mult^1.2）
            double base = 1.0 / Math.pow(multipliers[i], 1.2);

            // 若玩家某区下注多，避免撞高倍
            double betFactor = 1.0 / (1.0 + bets[i] * 0.4);

            double w = base * betFactor;

            weights[i] = w;
            sum += w;
        }

        // ------- 归一化 -------
        for (int i = 0; i < n; i++) {
            weights[i] = weights[i] / sum;
        }

        // ------- 随机抽取 -------
        double r = rand.nextDouble();
        double cumulative = 0;

        for (int i = 0; i < n; i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                return i + 1;   // 返回奖项编号
            }
        }

        return n;
    }


    /**
     * ************ 优化后的水洗（Wash）积分返还算法 ************
     *
     * 低倍率 → 水洗概率更高（自然鼓励低倍）
     * 激励积分返还（小额频繁返还，体验更好）
     */
    public static double calcWash(
            double[] bets,
            double[] multipliers,
            Random rand
    ) {
        double wash = 0;

        for (int i = 0; i < bets.length; i++) {

            // 基础概率（3%）
            double baseRate = 0.03;

            // 倍率越低 → 水洗概率越高
            double lowMultiBoost = 1.0 / multipliers[i];

            double washRate = baseRate * lowMultiBoost;

            // 限制在 Max 6%
            washRate = Math.min(washRate, 0.06);

            // 是否触发水洗
            if (rand.nextDouble() < washRate) {

                // 返还比例：1% ~ 3%
                double backRate = 0.01 + rand.nextDouble() * 0.02;

                wash += bets[i] * backRate;
            }
        }

        return wash;
    }


    /**
     * ************ 对外可直接调用的兑换 API ************
     */
    public static int getDrawNumber(double[] bets, double[] multipliers) {
        Random rand = new Random();

        int draw = drawNumberSafe(bets, multipliers, rand);
        double wash = calcWash(bets, multipliers, rand);

        System.out.println("开出奖项编号：" + draw + " ；积分返还（wash）：" + wash);

        return draw;
    }


    /**
     * ************ Demo 测试入口 ************
     */
    public static void main(String[] args) {

        // 模拟 9 个奖项
        double[] bets =        {10, 20, 15, 12, 5, 7,  3,  1,  2};
        double[] multipliers = {5,  10, 8,  6,  4, 12, 15, 20, 25};

        Random rand = new Random();

        int testRounds = 50;
        for (int i = 0; i < testRounds; i++) {
            int result = drawNumberSafe(bets, multipliers, rand);
            double wash = calcWash(bets, multipliers, rand);

            System.out.printf("第 %02d 次 → 开奖：%-2d  返还：%.2f%n", i + 1, result, wash);
        }

        System.out.println("\n👉 Demo 完成，可直接使用 getDrawNumber() 接口。");
    }
}
