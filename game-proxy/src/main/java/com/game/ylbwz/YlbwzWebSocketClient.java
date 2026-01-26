package com.game.ylbwz;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.RestTemplateUtils;
import com.game.redis.RedisService;
import com.game.utils.DateUtils;
import com.game.utils.DomainNameUtil;
import com.game.utils.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自定义WebSocket客户端
 */
@Slf4j
public class YlbwzWebSocketClient extends WebSocketClient {

    public static String SESSION = "";



    //用来接收数据
    private String excptMessage;

    private static RedisService redisService;

    /**
     * 线程安全的Boolean -是否受到消息
     */
    public AtomicBoolean hasMessage = new AtomicBoolean(false);

    /**
     * 线程安全的Boolean -是否已经连接
     */
    private AtomicBoolean hasConnection = new AtomicBoolean(false);

    /**
     * 构造方法
     *
     * @param serverUri
     */
    public YlbwzWebSocketClient(URI serverUri) {
        super(serverUri);
        redisService = SpringUtils.getBean(RedisService.class);
        log.info("幽林保卫战 init:" + serverUri.toString());
    }

    /**
     * 打开连接是方法
     *
     * @param serverHandshake
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("幽林保卫战 onOpen");
    }

    /**
     * 收到消息时
     *
     * @param s
     */
    @Override
    public void onMessage(String s) {
        hasMessage.set(true);
        if (!Objects.equals(s, "heart")) {
            JSONObject json = JSONObject.parseObject(s);
            Integer code = json.getInteger("code");
            if (code == 1) {
                long currentTime = System.currentTimeMillis();
                redisService.setCacheObject("lottery_open_time_ylbwz", currentTime);
                log.info(DateUtils.getTime() + " 幽林保卫战-设置开奖时间:{}", currentTime);


                for (String url : DomainNameUtil.urls) {
                    url+="/ylbwz/luckyMonster";
                    try {
                        ResponseEntity<String> responseEntity = RestTemplateUtils.post(url, s, String.class);
                        String resp = responseEntity.getBody();
                        log.info(url + "-幽林保卫战同步请求响应：{}", resp);
                    } catch (RestClientException e) {
                        log.info(url + "-幽林保卫战同步请求异常：{}", e.getMessage());
                    }
                }
                log.info(DateUtils.getTime() + " 幽林保卫战 onMessage 开奖:" + s + "|| 号码：" + json.getInteger("monsterId"));
            } else {
                log.info(DateUtils.getTime() + " 幽林保卫战 onMessage:" + s);
            }
        } else {
            log.info(DateUtils.getTime() + " 幽林保卫战 onMessage:" + s);
        }
    }

    public void sendMessage(String message) {
        try {
            this.send(message);
        } catch (Exception e) {

        }
        log.info("幽林保卫战 已发送消息：" + message);
    }

    /**
     * 当连接关闭时
     *
     * @param i
     * @param s
     * @param b
     */
    @Override
    public void onClose(int i, String s, boolean b) {
        this.hasConnection.set(false);
        this.hasMessage.set(false);
        log.info("幽林保卫战 onClose:" + s);
    }

    /**
     * 发生error时
     *
     * @param e
     */
    @Override
    public void onError(Exception e) {
        log.info("幽林保卫战 onError:" + e);
    }

    @Override
    public void connect() {
        if (!this.hasConnection.get()) {
            super.connect();
            hasConnection.set(true);
        }
    }

    //获取接收到的信息
    public String getExcptMessage() {
        if (excptMessage != null) {
            String message = new String(excptMessage);
            excptMessage = null;
            return message;
        }
        return null;
    }

    private static YlbwzWebSocketClient client;

    public static YlbwzWebSocketClient getInstance(String webSocketUri) {
        if (client == null || client.isClosed()) {
            try {
                //实例WebSocketClient对象，并连接到WebSocket服务端
                client = new YlbwzWebSocketClient(new URI(webSocketUri));
                client.connect();
                //等待服务端响应
                while (!client.getReadyState().equals(ReadyState.OPEN)) {
                    log.info("幽林保卫战 连接中···请稍后");
                    Thread.sleep(1000);
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            return client;
        }
        return client;
    }

    public static void closeClient(YlbwzWebSocketClient client) {
        client.close();
    }


    public static void login() {
        HttpRequest cookie = HttpUtil.createGet("http://changchang.maxxiang.com/changchang/user/login?phone=15641762326&password=aa123456");
        HttpResponse execute = cookie.execute();
        SESSION = execute.getCookieValue("SESSION");
    }
}

