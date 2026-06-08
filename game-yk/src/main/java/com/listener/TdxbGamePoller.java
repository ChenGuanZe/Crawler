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

    private static final String URL = "https://m.zhyy.net/api/game/gameApis";

    private static final String PAYLOAD_JSON =
            "{\"data\":\"i0/j6KrTBDNppC7QsyBwr6sTvv56ALWn8LE+vnGOw8Na+miJYUjVMdanPbORWMbcg5FKkw2bbMK36g+bQ7VODUhW2oXo18WqrExLFPfC7CdB2LbDzRBp0OtzQP4q8zE+\"}";

    private static final String COOKIE =
            "aws-waf-token=6ec895b2-7be1-4d01-9536-f772bc454e86:BgoAjvpXb5c+AAAA:yc2uENdPfKvMoTg0XncUwPyFvXJ91QBFa36V7N7aeudI7RVk2H+V51dItWUY7aqElUkyVN5ZqnTHPbdbEUW4n5rcHxYjQDNXkzM9uWnz5Nqew83/FuGMD4YuoIroRZAAH2xtJmMGBgZItmn/eXhUHuSgSa/C/GGaNNfxDlcd28uErY5ILkinQ/sufW2A52Jqr6wRLw307BeqlQpBBthr4+s8sfEQD6NFZvgcv0x/wgSyAMfdIqvB1mgFVUc=; token=1QUAADE1NTMzN2VmNWQxOGE4YmU5YmY2MTJmZWQxMTQ2ZTll; i18n_redirected=zh-CN";

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private Long lastXqTimeId = null;

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
            RequestBody body = RequestBody.create(PAYLOAD_JSON, JSON_TYPE);

            Request request = new Request.Builder()
                    .url(URL)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .header("cookie", COOKIE)
                    .header("user-agent", "Mozilla/5.0")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    log.warn("{} - HTTP错误: {}", GAME_NAME, response.code());
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode root = mapper.readTree(responseBody);
                JsonNode xqNode = root.get("XQtimeid");

                if (xqNode != null && !xqNode.isNull()) {
                    long currentXqTimeId = xqNode.asLong();

                    if (lastXqTimeId != null && currentXqTimeId != lastXqTimeId) {
                        log.info("{} - XQtimeid变化: {} -> {}", GAME_NAME, lastXqTimeId, currentXqTimeId);
                        log.info("{} - 原始响应数据: {}", GAME_NAME, responseBody);

                        JsonNode bqWinNode = root.path("BQwin");
                        log.info("{} - BQwin节点类型: {}, 内容: {}", GAME_NAME, bqWinNode.getNodeType(), bqWinNode);

                        JsonNode winnerNode = bqWinNode.size() > 0
                                ? bqWinNode.get(0)
                                : null;

                        if (winnerNode != null) {
                            log.info("{} - winnerNode类型: {}, 内容: {}, asInt: {}", GAME_NAME, winnerNode.getNodeType(), winnerNode, winnerNode.asInt());
                            sendLotteryResult(winnerNode);
                        } else {
                            log.warn("{} - BQwin为空，无法获取开奖结果", GAME_NAME);
                        }

                        sendGameTime();
                    }

                    lastXqTimeId = currentXqTimeId;
                }
            }

        } catch (Exception e) {
            log.error("{} - trySync异常: {}", GAME_NAME, e.getMessage());
        }
    }

    /**
     * 老版本 rewardId -> monsterId 映射（来疯直播原始ID对应关系）
     * 如果新平台返回的值已经是 1-8，则不会命中映射，直接用原值
     */
    private int mapMonsterId(int rawId) {
        switch (rawId) {
            case 698: return 8;  // 凤舞
            case 699: return 7;  // 黑石
            case 700: return 2;  // 蓝海
            case 701: return 1;  // 龙鳞
            case 702: return 6;  // 绿洲岛
            case 703: return 5;  // 梦境岛
            case 704: return 4;  // 银月岛
            case 705: return 3;  // 紫烟岛
            default: return rawId;
        }
    }

    private void sendLotteryResult(JsonNode winnerNode) {
        try {
            int rawId = winnerNode.has("Pid") ? winnerNode.get("Pid").asInt() : winnerNode.asInt();
            int monsterId = mapMonsterId(rawId);
            log.info("{} - 原始开奖ID: {}, 映射后monsterId: {}, 原始节点: {}", GAME_NAME, rawId, monsterId, winnerNode);

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
