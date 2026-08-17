package com.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utils.DomainNameUtil;
import com.utils.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 探岛寻宝 游戏轮询器（新版）
 */
@Slf4j
public class TdxbGamePoller {

    private static final String GAME_NAME = "探岛寻宝";
    private static final int GAME_ID = 29;

    // ===== 来疯(laifeng)探岛寻宝 新数据源 =====
    private static final String URL =
            "https://yapi.laifeng.com/lftop/mtop.youku.laifeng.Interstellar.scene.info/2.0/";
    private static final String ENAME = "tandaoxunbao1";
    private static final String APP_KEY = "24679788";
    private static final String UTDID = "70e747d5-ef89-4a26-b1e4-5395deac1a71";

    // 登录态会过期，失效后从浏览器 Request Headers 里复制最新的完整 Cookie 替换这里
    private static final String COOKIE =
            "UM_distinctid=19f7e5167691416-07a973cbee65aa8-26071951-384000-19f7e51676a15a5; cna=1f025911533e405abcee32849a154d5f; fansTuan-tips=vistived; __ysuid=1784530702891zsy; xlly_s=1; L_pck_rm=nkHPCh%2B0GFDuLPMcUJEpOFpGx3onTttU3rQiW6pm0KLUceBR96gs3hz0kOrKtr2TySSS5DhYxPdDVfzgT3XFwdttqNGANee79ikwJtkOz6as%2FMyx5p74UQ%2B9A4l7NJwt6tz0R8hEc6rx%2Fly%2B2Qsa8ba6Qb3VLIYzqHA3W04lEGM%3D; isg=BAgI5cuA9nY5KRrytbkpvCGR2XYasWy7KJp4HsK4VwN2na4HfsHVS3leFXXtrSST; laifeng_react_page_xingzuoV2_firstShow=2026-07-22; _c_WBKFRo=nweo8uTtyrr6wIAPA1mkuqlP4IPEgSfOUBFUAeDH; _nb_ioWEgULi=; tfstk=gWFZKl1IJ1CZE2IuzzcVYyRc5xGTOjS5bSijor4m5cmilrbqglZ7CxN_omr4ADLMiAmmxy0mDcq4kN3mmoqqcR6CNPUtMjj5g_17WithCYVZinan6qwXUecPNPU9-cgVXL553rWfTqm0mAmHtqmHmCAgmvAnkDAioC0DxwuxxjAiiAvntqgxmdA0mybEkDmmijqmtwuxxmcmibfTmldEYAb9uYPyeenEQ04iYQzLTV4JBPmeiIVULAoucDRDiWuakP7K4QWjq8nsF0ZhOCl4-qrsAufkTXDzerg4b1Ri1J2L6fNC4UHa0DDu9vWk8ula0YViLEAjOfonT2VllLnQTcZ0_vbWaxG3NYcgd9Ishfuzm5EwuIquRzNtpSSyxmesyX07W1REZP0c4_-x-GEYMR-D3A0K80_F85rmmvGvU2PkHKHFp2o5yapvHAfq80_erKpxKa3EVN2A.";    // ===== 新版判定：rewardId 已不可靠，改用中奖岛屿图片(rightIcon)里的图片哈希匹配 → 岛屿ID(=monsterId 1-8) =====
    private static final Map<String, Integer> IMAGE_RESULT_MAP = new HashMap<>();
    static {
        IMAGE_RESULT_MAP.put("96885BA8960C4FE8BA7E404C2EA9600D", 1); // 龙鳞岛
        IMAGE_RESULT_MAP.put("2D3E1A07423A49448571CED302A27EFB", 2); // 蓝海岛
        IMAGE_RESULT_MAP.put("D204A4CF94EB4204BDBDFE0FF57E733C", 3); // 紫烟岛
        IMAGE_RESULT_MAP.put("3810773120B941508027E3BF27F3D2F8", 4); // 银月岛
        IMAGE_RESULT_MAP.put("DC507F928E5D4F89B4BF7F8F214CC160", 5); // 梦境岛
        IMAGE_RESULT_MAP.put("BDDC472AFADB496BADF418E5AAC69F0A", 6); // 绿洲岛
        IMAGE_RESULT_MAP.put("02221D45ADEF49F4AB28A649FCFA2B7F", 7); // 黑石岛
        IMAGE_RESULT_MAP.put("B37482DCFE5A4C56AF0B2C1600678933", 8); // 凤舞岛
    }

    // 最近一期结果的快照，供 TdxbWsPoller 做新旧双源对账(切换数据源前确认号码口径一致)
    private static volatile Integer lastIslandId = null;
    private static volatile long lastIslandTime = 0L;

    public static Integer getLastIslandId() {
        return lastIslandId;
    }

    public static long getLastIslandTime() {
        return lastIslandTime;
    }

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private String lastScene = null;

    private long lastChangeMs = System.currentTimeMillis(); // 上次真正出新一期开奖的时间
    private long lastWarnMs = 0L;                            // 告警节流
    private static final long STALL_WARN_MS = 3 * 60 * 1000; // 超过3分钟没新一期就告警
    private static final long WARN_THROTTLE_MS = 60 * 1000;  // 告警最多每60秒一条

    public TdxbGamePoller() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        this.mapper = new ObjectMapper();
    }

    public synchronized void trySync() {
        try {
            // 来疯 mtop 接口: 表单提交 ename/appKey/info(clientInfo)
            RequestBody body = new FormBody.Builder()
                    .add("ename", ENAME)
                    .add("appKey", APP_KEY)
                    .add("info", buildInfoJson())
                    .build();

            Request request = new Request.Builder()
                    .url(URL)
                    .header("Accept", "*/*")
                    .header("Origin", "https://v.laifeng.com")
                    .header("Referer", "https://v.laifeng.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
                    .header("Cookie", COOKIE)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.code() != 200) {
                    warnThrottled("{} - HTTP错误: {}，原始响应: {}", GAME_NAME, response.code(), brief(responseBody));
                    return;
                }

                JsonNode root = mapper.readTree(responseBody);
                // mtop 结构: { "data": { "betStatus":.., "scene":.., "rewardId":.. }, "ret":[..] }
                JsonNode data = root.path("data");
                JsonNode betStatusNode = data.get("betStatus");
                JsonNode sceneNode = data.get("scene");

                if (betStatusNode == null || sceneNode == null) {
                    // 缺关键字段：几乎可确定是登录态过期/接口返回错误体
                    warnThrottled("{} - 响应缺少 betStatus/scene(疑似cookie过期或上游异常)，原始响应: {}",
                            GAME_NAME, brief(responseBody));
                    return;
                }

                int betStatus = betStatusNode.asInt(-1);
                String scene = sceneNode.asText();

                // 只有开奖阶段(betStatus==3)才是一期结果；否则是下注/等待阶段，正常跳过
                if (betStatus != 3) {
                    long stall = System.currentTimeMillis() - lastChangeMs;
                    if (stall > STALL_WARN_MS) {
                        warnThrottled("{} - 已 {} 秒没有新开奖(betStatus={}, scene={})，上游可能停摆或登录态过期，最新响应: {}",
                                GAME_NAME, stall / 1000, betStatus, scene, brief(responseBody));
                    }
                    return;
                }

                // 同一 scene(一期)只处理一次，避免开奖阶段被重复推送
                if (scene.equals(lastScene)) {
                    return;
                }

                // 【新版判定】开奖岛屿从中奖图片 rightIcon 的图片哈希匹配得到(1-8)；rewardId 已不可靠不再使用
                String rightIcon = data.path("rightIcon").asText("");
                int islandId = matchIslandByIcon(rightIcon);
                if (islandId <= 0) {
                    // 开奖阶段却拿不到有效岛屿图片 = 掉奖，节流告警(同一场只提醒一次靠 lastScene 之外单独判断)
                    warnThrottled("{} - 掉奖：开奖阶段未命中岛屿图片(scene={}, rightIcon={})，最新响应: {}",
                            GAME_NAME, scene, rightIcon, brief(responseBody));
                    return;
                }

                lastScene = scene;
                lastChangeMs = System.currentTimeMillis();
                lastIslandId = islandId;
                lastIslandTime = lastChangeMs;

                log.info("{} - 新开奖 scene={}, 岛屿ID(monsterId)={}, rightIcon={}",
                        GAME_NAME, scene, islandId, rightIcon);

                sendLotteryResult(islandId);
                sendGameTime();
            }

        } catch (Exception e) {
            log.error("{} - trySync异常: {}", GAME_NAME, e.getMessage());
        }
    }

    /** 来疯 mtop info 参数(clientInfo) */
    private String buildInfoJson() {
        return "{\"clientInfo\":{"
                + "\"appName\":\"laifengPc\","
                + "\"appVersion\":\"1.0.0\","
                + "\"appKey\":\"" + APP_KEY + "\","
                + "\"appId\":1000,"
                + "\"deviceModel\":\"网页端\","
                + "\"brand\":\"Windows\","
                + "\"osVersion\":\"Windows 10.0\","
                + "\"utdid\":\"" + UTDID + "\"}}";
    }

    /** 节流告警：最多每 WARN_THROTTLE_MS 打一条，避免刷屏 */
    private void warnThrottled(String format, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastWarnMs >= WARN_THROTTLE_MS) {
            lastWarnMs = now;
            log.warn(format, args);
        }
    }

    /** 截断过长响应，日志里只留前512字符 */
    private String brief(String s) {
        if (s == null) return "";
        return s.length() > 512 ? s.substring(0, 512) + "...(truncated)" : s;
    }

    /**
     * 从中奖图片地址 rightIcon 里匹配图片哈希 → 岛屿ID(=monsterId 1-8)。
     * 命中返回 1-8；未命中(空/未知图片/掉奖)返回 0。
     */
    private int matchIslandByIcon(String rightIcon) {
        if (rightIcon == null || rightIcon.trim().isEmpty()) {
            return 0;
        }
        String upper = rightIcon.toUpperCase(java.util.Locale.ROOT);
        for (Map.Entry<String, Integer> e : IMAGE_RESULT_MAP.entrySet()) {
            if (upper.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return 0;
    }

    private void sendLotteryResult(int monsterId) {
        try {
            log.info("{} - 开奖岛屿ID(monsterId): {}", GAME_NAME, monsterId);

            Map<String, Object> params = new HashMap<>();
            params.put("monsterId", monsterId);
            params.put("code", 1);

            String jsonParams = mapper.writeValueAsString(params);
            log.info("发送开奖数据==》{}", jsonParams);
            for (String url : DomainNameUtil.urls) {
                try {
                    String fullUrl = url + "/tdxb/luckyMonster";
                    String resp = OkHttpUtil.postJson(fullUrl, jsonParams);
                    log.info("{} - 开奖结果同步请求响应：{} => {}", GAME_NAME, fullUrl, resp);
                } catch (Exception e) {
                    log.warn("{} - 开奖结果同步请求异常：{}", GAME_NAME, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("{} - 发送开奖结果异常", GAME_NAME, e);
        }
    }

    private void sendGameTime() {
        long opentime = System.currentTimeMillis() + 60 * 1000;  // 当前时间 + 60秒（新版本）

        for (String url : DomainNameUtil.transitUrls) {
            try {
                String fullUrl = url + "/gameProxy/proxy/setGameTime?gameId=" + GAME_ID + "&time=" + opentime;
                String resp = OkHttpUtil.get(fullUrl, null);
                log.info("{} - 发送游戏开始时间：{} => {}", GAME_NAME, fullUrl, resp);
            } catch (Exception e) {
                log.warn("{} - 发送游戏开始时间异常：{}", GAME_NAME, e.getMessage());
            }
        }
    }
}
