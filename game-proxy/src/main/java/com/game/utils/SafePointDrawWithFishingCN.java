package com.game.utils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * SafePointDrawWithFishingCN
 *
 * 完整版：
 * - 抽奖 + 水洗（返还）+ 补偿 + 捕鱼（游戏化）
 * - 下注平滑（避免超大注 10000 拉满概率）
 * - 强随机扰动（避免用户看出“压哪开哪”）
 * - 单例模式（保持每日统计）
 * - 高倍独立每日上限控制
 */
public class SafePointDrawWithFishingCN {

    // ================= 单例实现 =================
    private static SafePointDrawWithFishingCN INSTANCE = null;

    public static synchronized SafePointDrawWithFishingCN getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SafePointDrawWithFishingCN(new Random(), 25.0);
        }
        return INSTANCE;
    }

    public static int getDrawNumber(double[] bets, double[] multipliers) {
        return getInstance().getDrawResult(bets, multipliers);
    }

    // ================= 配置参数 =================
    private static final double HIGH_MULTIPLIER_THRESHOLD = 20.0; // >=20视为高倍率

    private final Random rand;

    private final double baseMultiplierDecay = 0.08;  //降低倍率基础衰减   值也低开出来的概率也高
    private final double newUserHighMultiplierFactor = 0.8;
    private final double highMultiplierPenaltyFactor = 0.05;

    private final double baseCaptureRate = 0.6;
    private final double critChance = 0.05;
    private final double critMultiplier = 1.5;

    private final int consolationLossThreshold = 5;
    private final double consolationRewardPoints = 5.0;

    private final double maxAllowedMultiplier;

    // ================= 多高倍每日上限配置 =================
    private static final Map<Double, Integer> MULTIPLIER_DAILY_LIMIT = new HashMap<>();
    static {
        MULTIPLIER_DAILY_LIMIT.put(50.0, 60);
        MULTIPLIER_DAILY_LIMIT.put(40.0, 120);
        MULTIPLIER_DAILY_LIMIT.put(30.0, 240);
        MULTIPLIER_DAILY_LIMIT.put(20.0, 300);
    }

    // 今日已开次数
    private static final Map<Double, Integer> multiplierCountToday = new HashMap<>();
    private static int lastDay = -1;

    // ================= 构造函数 =================
    private SafePointDrawWithFishingCN(Random rand, double maxAllowedMultiplier) {
        this.rand = rand;
        this.maxAllowedMultiplier = maxAllowedMultiplier;
        lastDay = LocalDate.now().getDayOfYear();
        multiplierCountToday.clear();
    }

    // ================= 每日重置 =================
    private void resetDailyMultiplierCounters() {
        int today = LocalDate.now().getDayOfYear();
        if (today != lastDay) {
            multiplierCountToday.clear();
            lastDay = today;
        }
    }

    // ================= 下注平滑 =================
    private double normalizeBet(double bet) {
        if (bet <= 0) return 1.0;
        if (bet <= 300.0) return Math.sqrt(bet);
        return Math.sqrt(300.0) + Math.log(bet - 299.0);
    }

    // ================= 核心抽奖 =================
    private int drawPrizeIndex1ToN(double[] bets, double[] multipliers, boolean isNewUser) {
        resetDailyMultiplierCounters();

        int n = bets.length;
        if (n == 0 || multipliers.length != n) return -1;

        double[] weights = new double[n];
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            double w = 5.0; // 基础底值
            w += normalizeBet(bets[i]);
            if (multipliers[i] > maxAllowedMultiplier) w *= 0.1;

            w /= (1.0 + multipliers[i] * baseMultiplierDecay);

            if (multipliers[i] >= HIGH_MULTIPLIER_THRESHOLD) {
               // w *= Math.pow(0.5, multiplierCountToday.getOrDefault(multipliers[i], 0));
                int count = multiplierCountToday.getOrDefault(multipliers[i], 0);
                w *= Math.max(0.4, 1.0 - count * 0.08);
            }

            if (isNewUser && multipliers[i] >= 12.0) w *= newUserHighMultiplierFactor;

            // 高倍每日上限控制
            if (MULTIPLIER_DAILY_LIMIT.containsKey(multipliers[i])) {
                int used = multiplierCountToday.getOrDefault(multipliers[i], 0);
                int limit = MULTIPLIER_DAILY_LIMIT.get(multipliers[i]);
                if (used >= limit) w *= 0.04; // 达到上限 → 极低概率
            }

            // 强随机噪声
            double noise = 0.55 + rand.nextDouble() * 1.2;
            w *= noise;

            weights[i] = w;
            sum += w;
        }

        double r = rand.nextDouble() * sum;
        double cumulative = 0.0;
        int chosen = n - 1;
        for (int i = 0; i < n; i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                chosen = i;
                break;
            }
        }

        double hitMul = multipliers[chosen];
        if (MULTIPLIER_DAILY_LIMIT.containsKey(hitMul)) {
            multiplierCountToday.put(hitMul, multiplierCountToday.getOrDefault(hitMul, 0) + 1);
        }

        return chosen + 1;
    }

    public int getDrawResult(double[] bets, double[] multipliers) {
        return drawPrizeIndex1ToN(bets, multipliers, false);
    }

    // ================= 水洗返还算法 =================
    public double calcWashReturn(boolean isNewUser, double baseCost, double hitMultiplier) {
        double baseRate = isNewUser ? 0.04 : 0.02;
        double maxRate = isNewUser ? 0.08 : 0.04;

        double lowMultiBoost = hitMultiplier > 0 ? (1.0 / hitMultiplier) : 1.0;
        double washProb = Math.min(maxRate, baseRate * (1.0 + lowMultiBoost * 1.5));

        if (rand.nextDouble() >= washProb) return 0.0;

        double backMin = isNewUser ? 0.01 : 0.005;
        double backMax = isNewUser ? 0.04 : 0.02;

        double rate = backMin + rand.nextDouble() * (backMax - backMin);
        return baseCost * rate;
    }

    // ================= 补偿逻辑 =================
    public double calcConsolationIfEligible(int cnt) {
        if (cnt >= consolationLossThreshold) return consolationRewardPoints + (cnt - consolationLossThreshold);
        return 0.0;
    }

    // ================= 捕鱼模块 =================
    public static class Fish {
        public final String name;
        public final double hp;
        public final double multiplier;
        public final double baseReward;

        public Fish(String name, double hp, double multiplier, double baseReward) {
            this.name = name;
            this.hp = hp;
            this.multiplier = multiplier;
            this.baseReward = baseReward;
        }
    }

    public static class FishingResult {
        public final boolean caught;
        public final int bulletsUsed;
        public final double rewardPoints;
        public final double captureProb;
        public final boolean crit;

        public FishingResult(boolean c, int b, double r, double cp, boolean crit) {
            this.caught = c;
            this.bulletsUsed = b;
            this.rewardPoints = r;
            this.captureProb = cp;
            this.crit = crit;
        }

        @Override
        public String toString() {
            return String.format("是否捕获=%b，使用子弹=%d，奖励=%.2f，捕获概率=%.4f，暴击=%b",
                    caught, bulletsUsed, rewardPoints, captureProb, crit);
        }
    }

    public FishingResult attemptCatchFish(Fish fish, double damagePerBullet, int maxBullets, boolean isNewUser) {
        double accumulated = 0.0;
        int bullets = 0;
        boolean critHit = false;

        while (bullets < maxBullets) {
            bullets++;
            accumulated += damagePerBullet;

            double ratio = Math.min(1.0, accumulated / fish.hp);
            double captureProb = baseCaptureRate * ratio;

            boolean crit = rand.nextDouble() < critChance;
            if (crit) {
                captureProb = Math.min(1.0, captureProb * critMultiplier);
                critHit = true;
            }

            if (isNewUser) captureProb = Math.min(1.0, captureProb * 1.05);

            if (rand.nextDouble() < captureProb) {
                double reward = fish.baseReward * fish.multiplier;
                if (critHit) reward *= 1.1;
                return new FishingResult(true, bullets, reward, captureProb, critHit);
            }
        }

        return new FishingResult(false, bullets, 0.0,
                baseCaptureRate * Math.min(1.0, accumulated / fish.hp),
                false);
    }

    // ================= 一轮完整结果 =================
    public static class RoundResult {
        public final int prizeIndex;
        public final double multiplier;
        public final double payout;
        public final double washReturn;
        public final double consolation;
        public final int newLoseCount;
        public final Map<Double, Integer> highMultiplierCountMap;
        public final FishingResult fishResult;

        public RoundResult(int prizeIndex, double multiplier, double payout, double washReturn, double consolation,
                           int newLoseCount, Map<Double, Integer> highMultiplierCountMap, FishingResult fishResult) {
            this.prizeIndex = prizeIndex;
            this.multiplier = multiplier;
            this.payout = payout;
            this.washReturn = washReturn;
            this.consolation = consolation;
            this.newLoseCount = newLoseCount;
            this.highMultiplierCountMap = new HashMap<>(highMultiplierCountMap);
            this.fishResult = fishResult;
        }

        @Override
        public String toString() {
            return String.format(
                    "开奖=%d 倍率=%.0f 中奖=%.2f 洗分=%.2f 补偿=%.2f 连输=%d 今日高倍=%s\n捕鱼：%s",
                    prizeIndex, multiplier, payout, washReturn, consolation,
                    newLoseCount, highMultiplierCountMap, fishResult
            );
        }
    }

    public RoundResult executeOneRound(double[] bets, double[] multipliers, boolean isNewUser,
                                       int userLoseCountBefore, Fish fish,
                                       double damagePerBullet, int maxBullets) {

        int prizeIndex = drawPrizeIndex1ToN(bets, multipliers, isNewUser);
        double hitMulti = multipliers[prizeIndex - 1];
        double payout = bets[prizeIndex - 1] * hitMulti;

        double totalBet = 0.0;
        for (double b : bets) totalBet += b;

        double washReturn = calcWashReturn(isNewUser, totalBet, hitMulti);

        int loseCnt = userLoseCountBefore;
        if (payout < 50.0) loseCnt++;
        else loseCnt = 0;

        double consolation = calcConsolationIfEligible(loseCnt);

        FishingResult fishResult = attemptCatchFish(fish, damagePerBullet, maxBullets, isNewUser);

        return new RoundResult(prizeIndex, hitMulti, payout, washReturn, consolation, loseCnt, multiplierCountToday, fishResult);
    }

    // ================= 测试 main =================
    public static void main(String[] args) {
        SafePointDrawWithFishingCN engine = SafePointDrawWithFishingCN.getInstance();

        double[] multipliers = {5, 10, 8, 6, 4, 12, 15, 20, 30, 40, 50};
        double[] bets =       {0, 0, 0, 0, 0, 0, 0, 100, 50, 30, 10};

        Fish fish = new Fish("大鱼", 300, 10, 40);

        for (int i = 0; i < 50; i++) {
            RoundResult rr = engine.executeOneRound(bets, multipliers, false, 0, fish, 50.0, 8);
            System.out.println(rr);
        }
    }
}
