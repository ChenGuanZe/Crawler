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
 * 银河探险 游戏轮询器
 */
@Slf4j
public class YhtxGamePoller {

    private static final String GAME_NAME = "银河探险";
    private static final int GAME_ID = 31;

    private static final String URL = "https://m.zhyy.net/api/game/gameApis";

    private static final String PAYLOAD_JSON =
            "{\"data\":\"i0/j6KrTBDNppC7QsyBwr6sTvv56ALWn8LE+vnGOw8Na+miJYUjVMdanPbORWMbcilNpLi0ET1jOIe5q1DvyYEpG9aieTuLPjLhTa1xWH3hI3G7UvoFKU3duB5KSSgDf\"}";

    private static final String COOKIE = "aws-waf-token=6ec895b2-7be1-4d01-9536-f772bc454e86:BgoAjvpXb5c+AAAA:yc2uENdPfKvMoTg0XncUwPyFvXJ91QBFa36V7N7aeudI7RVk2H+V51dItWUY7aqElUkyVN5ZqnTHPbdbEUW4n5rcHxYjQDNXkzM9uWnz5Nqew83/FuGMD4YuoIroRZAAH2xtJmMGBgZItmn/eXhUHuSgSa/C/GGaNNfxDlcd28uErY5ILkinQ/sufW2A52Jqr6wRLw307BeqlQpBBthr4+s8sfEQD6NFZvgcv0x/wgSyAMfdIqvB1mgFVUc=; token=1AUAAGZlODQxNzk1YzVjMTNhOWJjNjA3NzNkZGYzNzRlMzE4; i18n_redirected=zh-CN";

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private Long lastXqTimeId = null;

    public YhtxGamePoller() {
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

                    if (lastXqTimeId == null) {
                        // 首次启动：不推送但要记录 baseline，避免重启时把当前正在进行的那期当成新期推送
                        log.info("{} - 首次启动，记录 baseline XQtimeid={}", GAME_NAME, currentXqTimeId);
                        lastXqTimeId = currentXqTimeId;
                    } else if (currentXqTimeId != lastXqTimeId) {
                        log.info("{} - XQtimeid变化: {} -> {}", GAME_NAME, lastXqTimeId, currentXqTimeId);
                        log.info("{} - 原始响应数据: {}", GAME_NAME, responseBody);

                        JsonNode bqWinNode = root.path("BQwin");
                        log.info("{} - BQwin节点类型: {}, 内容: {}", GAME_NAME, bqWinNode.getNodeType(), bqWinNode);

                        JsonNode winnerNode = bqWinNode.size() > 0
                                ? bqWinNode.get(0)
                                : null;

                        boolean pushOk = false;
                        if (winnerNode != null) {
                            log.info("{} - winnerNode类型: {}, 内容: {}, asInt: {}", GAME_NAME, winnerNode.getNodeType(), winnerNode, winnerNode.asInt());
                            pushOk = sendLotteryResult(winnerNode);
                        } else {
                            log.warn("{} - BQwin为空，无法获取开奖结果", GAME_NAME);
                        }

                        // 发送下期游戏开始时间（独立于开奖推送）
                        sendGameTime();

                        // 只有推送成功才前进 baseline；失败则保留旧 lastXqTimeId，下一轮重试
                        if (pushOk) {
                            lastXqTimeId = currentXqTimeId;
                        } else {
                            log.warn("{} - 推送失败，保留 lastXqTimeId={} 下一轮重试当前期 {}", GAME_NAME, lastXqTimeId, currentXqTimeId);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("{} - trySync异常: {}", GAME_NAME, e.getMessage());
        }
    }

    /**
     * 发送开奖结果到第三方
     * @return 是否至少有一个目标推送成功
     */
    private boolean sendLotteryResult(JsonNode winnerNode) {
        boolean anySuccess = false;
        try {
            int winnerId = winnerNode.has("Pid") ? winnerNode.get("Pid").asInt() : winnerNode.asInt();
            log.info("{} - 解析开奖号码: Pid={}, 原始节点: {}", GAME_NAME, winnerId, winnerNode);

            Map<String, Object> params = new HashMap<>();
            params.put("gameSucc", winnerId);

            String jsonParams = mapper.writeValueAsString(params);
            log.info("{} - 推送的开奖数据 {}", GAME_NAME, params);
            for (String url : DomainNameUtil.urls) {
                String fullUrl = url + "/yhtx/luckyMonster";
                try {
                    String resp = OkHttpUtil.postJson(fullUrl, jsonParams);
                    log.info("{} - 开奖结果同步请求响应：{} => {}", GAME_NAME, fullUrl, resp);
                    anySuccess = true;
                } catch (Exception e) {
                    log.warn("{} - 开奖结果同步请求异常：{} => {}", GAME_NAME, fullUrl, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("{} - 发送开奖结果异常", GAME_NAME, e);
        }
        return anySuccess;
    }

    /**
     * 发送游戏开始时间
     */
    private void sendGameTime() {
        long opentime = System.currentTimeMillis() + 60 * 1000;  // 当前时间 + 60秒

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
