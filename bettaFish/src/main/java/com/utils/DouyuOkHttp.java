package com.utils;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Slf4j
public class DouyuOkHttp {

    private static final int ROOM_ID = 10045681;
    private static WebSocket ws;
    private static OkHttpClient client = new OkHttpClient.Builder().build();
    private static Timer heartbeatTimer;
    private static boolean running = false;
    // ---------------------- 外部直接调用的方法 ------------------------
    public static void startDouyu() {
        running = true;
        connect();
    }

    // ---------------------- 自动重连的方法 ------------------------
    private static void reconnect() {
        if (!running) return;

        System.out.println("连接断开，3 秒后重连...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }
        connect();
    }

    // ---------------------- 封装的连接方法 ------------------------
    private static void connect() {
        System.out.println("尝试连接斗鱼服务器...");

        Request req = new Request.Builder()
                .url("wss://danmuproxy.douyu.com:8502/")
                .addHeader("Origin", "https://www.douyu.com")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();

        ws = client.newWebSocket(req, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("连接成功");

                sendDouyuCmd("type@=loginreq/roomid@=" + ROOM_ID + "/");
                sendDouyuCmd("type@=joingroup/rid@=" + ROOM_ID + "/gid@=-9999/");

                startHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                List<Map<String, String>> messages = decodeDouyuBinary(bytes.asByteBuffer());
                for (Map<String, String> m : messages) {
                    if ("chatmsg".equals(m.get("type"))) {
                        System.out.println("【弹幕】" + m.get("nn") + "：" + m.get("txt"));
                    } else {
                        System.out.println("消息：" + m);
                        if (m != null && m.containsKey("status")) {
                            if (m.get("status").equals("2")) {
                                JSONObject params = new JSONObject();
                                int openNumber = Integer.parseInt(m.get("hitTower"));

                                /**
                                 * 1 洛阳
                                 * 2 成都
                                 * 3  建业
                                 * 4 荆州
                                 * 5  长安
                                 * 6 许昌
                                 * 7 汉中
                                 */
                                switch (openNumber){
                                    case 1:
                                        openNumber=7;
                                        break;
                                    case 2:
                                        openNumber=6;
                                        break;
                                    case 3:
                                        openNumber=5;
                                        break;
                                    case 4:
                                        openNumber=4;
                                        break;
                                    case 5:
                                        openNumber=3;
                                        break;
                                    case 6:
                                        openNumber=2;
                                        break;
                                    case 7:
                                        openNumber=1;
                                        break;

                                }
                                params.put("Number", openNumber);
                                log.info("大话三国开奖号码 {}", openNumber);
                                OkHttpClient client = new OkHttpClient();
                                for (int i = 0; i < DomainNameUtil.urls.length; i++) {
                                    String url = DomainNameUtil.urls[i] + "/luckyMonster";
                                    try {
                                        // JSON 请求体
                                        RequestBody body = RequestBody.create(
                                                params.toJSONString(),
                                                MediaType.parse("application/json; charset=utf-8")
                                        );

                                        // 构造请求
                                        Request request = new Request.Builder()
                                                .url(url)
                                                .post(body)
                                                .build();

                                        // 执行请求
                                        try (Response response = client.newCall(request).execute()) {

                                            if (!response.isSuccessful()) {
                                                log.info(url + "-斗鱼-大话三国-开奖结果同步请求异常：HTTP {}", response.code());
                                                continue;
                                            }

                                            String resp = response.body().string();
                                            log.info(url + "-斗鱼-大话三国-开奖结果同步请求响应：{}", resp);
                                        }

                                    } catch (Exception e) {
                                        log.info(url + "-斗鱼-大话三国-开奖结果同步请求异常：{}", e.getMessage());
                                    }
                                }


                                System.out.println("游戏结果");
                            } else if (m.get("status").equals("0")) {
                                System.out.println("游戏开始");
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.out.println("连接失败：" + t.getMessage());
                reconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("连接被关闭：" + reason);
                reconnect();
            }
        });
    }

    // ---------------------- 心跳包 ------------------------
    private static void startHeartbeat() {
        if (heartbeatTimer != null) heartbeatTimer.cancel();

        heartbeatTimer = new Timer();
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendDouyuCmd("type@=mrkl/");
            }
        }, 0, 30000);

        System.out.println("心跳包已启动");
    }

    // ---------------------- 发送斗鱼协议包 ------------------------
    private static void sendDouyuCmd(String text) {
        if (ws == null) return;

        byte[] msg = (text + "\0").getBytes(StandardCharsets.UTF_8);
        int len = 8 + msg.length;

        ByteBuffer buf = ByteBuffer.allocate(len + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.putInt(len);
        buf.putInt(len);
        buf.putShort((short) 689);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put(msg);

        ws.send(ByteString.of(buf.array()));
    }

    // ---------------------- 解码斗鱼二进制 ------------------------
    private static List<Map<String, String>> decodeDouyuBinary(ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        List<Map<String, String>> result = new ArrayList<>();

        while (buffer.remaining() >= 12) {
            buffer.mark();
            int length = buffer.getInt();
            if (buffer.remaining() < length - 1) {
                buffer.reset();
                break;
            }

            buffer.getInt();
            buffer.getShort();
            buffer.get();
            buffer.get();

            int txtLen = length - 10;
            byte[] t = new byte[txtLen];
            buffer.get(t);

            String s = new String(t, StandardCharsets.UTF_8);
            if (s.endsWith("\0")) s = s.substring(0, s.length() - 1);

            result.add(parseDouyuMessage(s));
        }

        return result;
    }

    private static Map<String, String> parseDouyuMessage(String msg) {
        Map<String, String> map = new LinkedHashMap<>();
        StringBuilder key = new StringBuilder();
        StringBuilder val = new StringBuilder();
        boolean readingKey = true;

        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);

            if (c == '/' && key.length() > 0) {
                map.put(key.toString(), val.toString());
                key.setLength(0);
                val.setLength(0);
                readingKey = true;
            } else if (c == '@' && i + 1 < msg.length()) {
                char next = msg.charAt(i + 1);
                if (next == '=') {
                    readingKey = false;
                    i++;
                } else if (next == 'S') {
                    val.append('/');
                    i++;
                } else if (next == 'A') {
                    val.append('@');
                    i++;
                } else {
                    if (readingKey) key.append(c);
                    else val.append(c);
                }
            } else {
                if (readingKey) key.append(c);
                else val.append(c);
            }
        }

        if (key.length() > 0) {
            map.put(key.toString(), val.toString());
        }

        return map;
    }

    // ---------------------- Demo ------------------------
    public static void main(String[] args) {
        startDouyu();
    }
}
