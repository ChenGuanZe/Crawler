package com.game.ylbwz;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.RestTemplateUtils;
import com.game.redis.RedisService;
import com.game.utils.DateUtils;
import com.game.utils.DomainNameUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import java.net.HttpCookie;
import java.util.Objects;

@Slf4j
public class YlbwzSocketClient {


    static WebSocket webSocket2 = null;

    private final static OkHttpClient client = new OkHttpClient();

    public static void start(String url, String session, String phone, RedisService redisService) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        WebSocketListener webSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                // 连接打开后发送消息
                webSocket.send("{\"action\":1,\"session\":\"" + session + "\"}");
            }

            @Override
            public void onMessage(WebSocket webSocket, String s) {
                if (!Objects.equals(s, "heart")) {
                    JSONObject json = JSONObject.parseObject(s);
                    Integer code = json.getInteger("code");
                    if (code == 1) {
                        long currentTime = System.currentTimeMillis();
                        redisService.setCacheObject("lottery_open_time_ylbwz", currentTime);
                        log.info(DateUtils.getTime() + " 幽林保卫战-设置开奖时间:{}", currentTime);


                        for (String url : DomainNameUtil.urls) {
                            url+="/qmGame/ylbwz/luckyMonster";
                            try {
                                ResponseEntity<String> responseEntity = RestTemplateUtils.post(url, s, String.class);
                                String resp = responseEntity.getBody();
                                log.info(url + "-幽林保卫战同步请求响应：{}", resp);
                            } catch (RestClientException e) {
                                log.info(url + "-幽林保卫战同步请求异常：{}", e.getMessage());
                            }
                        }
                        log.info(DateUtils.getTime() + " 幽林保卫战 onMessage 开奖:" + s + "|| 号码：" + json.getInteger("monsterId"));


                        HttpCookie httpCookie = new HttpCookie("SESSION", session);
                        HttpRequest cookie = HttpUtil.createGet("http://changchang.maxxiang.com/changchang/monster/bynum?num=2000&group=a").cookie(httpCookie);
                        HttpResponse execute = cookie.execute();
                        String resp = execute.body();

                        JSONObject json2 = JSONObject.parseObject(resp);
                        JSONObject data = json2.getJSONArray("data").getJSONObject(0);
                        log.info("幽林-获取历史数据请求响应：{} || 耗时：{}", data);
                    } else {
                        log.info(DateUtils.getTime() + " 幽林保卫战 onMessage:" + s);
                    }
                } else {
                    log.info(DateUtils.getTime() + " 幽林保卫战 onMessage:" + s);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("WebSocket closing: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                webSocket2 = client.newWebSocket(request, this);
                //t.printStackTrace();
            }
        };
        webSocket2 = client.newWebSocket(request, webSocketListener);
        while(true){
            try {
                webSocket2.send("{\"action\":777,\"session\":\"" + session + "\",\"phone\":\"" + phone + "\"}");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }
}


