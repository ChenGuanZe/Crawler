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
    private static final int GAME_ID = 31;  // TODO: 根据实际情况修改 gameId

    private static final String URL = "https://m.zhyy.net/api/game/gameApis";

    private static final String PAYLOAD_JSON =
            "{\"data\":\"i0/j6KrTBDNppC7QsyBwr6sTvv56ALWn8LE+vnGOw8Na+miJYUjVMdanPbORWMbcilNpLi0ET1jOIe5q1DvyYEpG9aieTuLPjLhTa1xWH3hI3G7UvoFKU3duB5KSSgDf\"}";

    private static final String COOKIE = "aws-waf-token=1c87d273-108e-4864-81b2-9b5139f0d246:BgoAb+00+PwAAAAA:wYVyoGWh/mII+qWp/Gqlhzc8sz2IagYpWf6wyI6vqjpAPADI3BEAqXgfQ/7BKVtd7NcvjsOAbGju5e+U0NU6S6dx9ZTWQjEm19+ijKN6AYAcwcN6ik/s8jXaUdk5krkj597mkGLT9ZrGWctWYgoYf7HUPqKbPA1KhYu3b+ez61kecl+CrBzBxm5m9zIWfu4YO7V2cF29K2bAdpjMIYbhS5t1mKxMISRXEa+B+uxM00NCGbY5P0y8qOPelsw=; language=zh-CN; token=Iw4AAGVkYWYzODg0MzdjZjA1ODRhZDUyNDM5NzM3NjNkNzJk; i18n_redirected=zh-CN";

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

                    if (lastXqTimeId != null && currentXqTimeId != lastXqTimeId) {
                        log.info("{} - XQtimeid变化: {} -> {}", GAME_NAME, lastXqTimeId, currentXqTimeId);

                        // 获取开奖结果
                        JsonNode winnerNode = root.path("BQwin").size() > 0
                                ? root.path("BQwin").get(0)
                                : null;

                        if (winnerNode != null) {
                            // 发送开奖结果到第三方
                            sendLotteryResult(winnerNode);
                        }

                        // 发送下期游戏开始时间
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
     * 发送开奖结果到第三方
     */
    private void sendLotteryResult(JsonNode winnerNode) {
        try {
            int winnerId = winnerNode.asInt();

            Map<String, Object> params = new HashMap<>();
            params.put("gameSucc", winnerId);  // 字段名改为 gameSucc

            String jsonParams = mapper.writeValueAsString(params);

            for (String url : DomainNameUtil.urls) {
                try {
                    String fullUrl = url + "/yhtx/luckyMonster";  // 接口路径改为 yhtx
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
