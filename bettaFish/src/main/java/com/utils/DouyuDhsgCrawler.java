package com.utils;

import com.alibaba.fastjson2.JSONObject;
import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 斗鱼大话三国 / 鼎力争雄 WebSocket 监听器。
 *
 * JDK8 可用版本，使用 OkHttp 3.14.9。
 * 在 bettaFish 中由 DwydhService.init() -> startDouyu() 拉起。
 *
 * 仅处理 defense_tower_session（大话三国），开奖后映射号码并推送到 wanshunGame：
 *   POST {DomainNameUtil.urls}/gameData/dhsg/luckyMonster   body={"Number": 1~7}
 * 推送失败最多重试 PUSH_MAX_ATTEMPTS 次，修偶发 connection refused / wanshunGame 重启瞬间丢期。
 */
public class DouyuDhsgCrawler {

    private static final int ROOM_ID = 10045681;
    private static final String WS_URL = "wss://danmuproxy.douyu.com:8502/";

    private static final String LOGIN_USERNAME = "141018420";
    private static final String LOGIN_UID = "141018420";
    private static final String LOGIN_VER = "20220825";
    private static final String LOGIN_AVER = "218101901";
    private static final String LOGIN_CT = "0";

    private static final int MAX_DEDUP_SIZE = 500;
    private static final int RECONNECT_BASE_DELAY_SECONDS = 3;
    private static final int RECONNECT_MAX_DELAY_SECONDS = 30;

    /** 开奖推送失败重试次数（修偶发 connection refused / wanshunGame 重启瞬间丢期） */
    private static final int PUSH_MAX_ATTEMPTS = 3;
    /** 两次重试间隔 */
    private static final long PUSH_RETRY_INTERVAL_MS = 1000L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private static final DedupCache dedupCache = new DedupCache(MAX_DEDUP_SIZE);

    private static volatile int reconnectDelaySeconds = RECONNECT_BASE_DELAY_SECONDS;
    private static volatile WebSocket ws;
    private static volatile ScheduledFuture<?> heartbeatFuture;

    private static final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(namedThreadFactory("douyu-heartbeat"));
    private static final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(namedThreadFactory("douyu-reconnect"));
    private static final ScheduledExecutorService pushExecutor =
            Executors.newScheduledThreadPool(2, namedThreadFactory("douyu-push"));

    private static final OkHttpClient client = buildClient();

    public static void main(String[] args) {
        startDouyu();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                stopDouyu();
                heartbeatExecutor.shutdownNow();
                reconnectExecutor.shutdownNow();
                pushExecutor.shutdownNow();
                System.out.println("[" + nowStr() + "] 程序已停止");
            }
        }));
    }

    public static void startDouyu() {
        if (!running.compareAndSet(false, true)) {
            log("斗鱼爬虫已经在运行，无需重复启动");
            return;
        }
        reconnectDelaySeconds = RECONNECT_BASE_DELAY_SECONDS;
        connect();
    }

    public static void stopDouyu() {
        running.set(false);
        reconnecting.set(false);
        stopHeartbeat();

        WebSocket current = ws;
        ws = null;
        if (current != null) {
            try {
                current.close(1000, "stop");
            } catch (Exception e) {
                current.cancel();
            }
        }
    }

    private static void connect() {
        if (!running.get()) {
            return;
        }

        log("尝试连接斗鱼服务器...");

        Request request = new Request.Builder()
                .url(WS_URL)
                .addHeader("Origin", "https://www.douyu.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                ws = webSocket;
                reconnecting.set(false);
                reconnectDelaySeconds = RECONNECT_BASE_DELAY_SECONDS;

                log("连接成功 | 房间号: " + ROOM_ID);
                sendInitPackets(webSocket);
                startHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                try {
                    List<Map<String, String>> messages = decodeDouyuBinary(bytes.toByteArray());
                    if (messages.isEmpty()) {
                        return;
                    }
                    for (Map<String, String> message : messages) {
                        try {
                            handleOneMessage(webSocket, message);
                        } catch (Exception e) {
                            log("处理单条消息异常 | message=" + message + " | err=" + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log("解析斗鱼二进制消息异常 | " + e.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // 斗鱼正常是二进制协议，文本消息忽略
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log("连接失败 | " + (t == null ? "null" : t.getMessage()));
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log("连接被关闭 | code=" + code + " | reason=" + reason);
                scheduleReconnect();
            }
        });
    }

    private static void sendInitPackets(WebSocket webSocket) {
        // 1. 先取消一个订阅，按抓包对齐
        sendDouyuCmd(webSocket, "type@=unsub/mt@=dyh_legend_subscribe/");

        // 2. 完整 loginreq
        sendDouyuCmd(webSocket, buildLoginReq(ROOM_ID));

        // 3. 入组
        sendDouyuCmd(webSocket, "type@=joingroup/rid@=" + ROOM_ID + "/gid@=-9999/");

        // 4. 立即发一次 mrkl
        sendDouyuCmd(webSocket, "type@=mrkl/");

        // 5. 补两个订阅
        sendDouyuCmd(webSocket, "type@=sub/mt@=dfansrk/");
        sendDouyuCmd(webSocket, "type@=sub/mt@=fansnum/");
    }

    private static String buildLoginReq(int roomId) {
        return "type@=loginreq/"
                + "roomid@=" + roomId + "/"
                + "dfl@=/"
                + "username@=" + LOGIN_USERNAME + "/"
                + "uid@=" + LOGIN_UID + "/"
                + "ver@=" + LOGIN_VER + "/"
                + "aver@=" + LOGIN_AVER + "/"
                + "ct@=" + LOGIN_CT + "/";
    }

    private static void scheduleReconnect() {
        stopHeartbeat();

        if (!running.get()) {
            return;
        }
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        final int delay = reconnectDelaySeconds;
        reconnectDelaySeconds = Math.min(reconnectDelaySeconds * 2, RECONNECT_MAX_DELAY_SECONDS);
        log("连接断开，" + delay + " 秒后重连...");

        reconnectExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!running.get()) {
                    reconnecting.set(false);
                    return;
                }
                reconnecting.set(false);
                connect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private static void startHeartbeat() {
        stopHeartbeat();
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    sendDouyuCmd(ws, "type@=mrkl/");
                } catch (Exception e) {
                    log("发送心跳异常 | " + e.getMessage());
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
        log("心跳包已启动");
    }

    private static void stopHeartbeat() {
        ScheduledFuture<?> future = heartbeatFuture;
        heartbeatFuture = null;
        if (future != null) {
            future.cancel(true);
        }
    }

    private static void sendDouyuCmd(WebSocket webSocket, String text) {
        if (webSocket == null || text == null) {
            return;
        }
        try {
            webSocket.send(ByteString.of(encodeDouyu(text)));
        } catch (Exception e) {
            log("发送斗鱼协议包异常 | text=" + text + " | " + e.getMessage());
        }
    }

    private static byte[] encodeDouyu(String text) {
        byte[] msg = (text + "\0").getBytes(StandardCharsets.UTF_8);
        int length = 8 + msg.length;

        ByteBuffer buffer = ByteBuffer.allocate(length + 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(length);
        buffer.putShort((short) 689);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        buffer.put(msg);
        return buffer.array();
    }

    private static List<Map<String, String>> decodeDouyuBinary(byte[] payload) {
        if (payload == null || payload.length < 12) {
            return Collections.emptyList();
        }

        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        int offset = 0;
        int totalLen = payload.length;

        while (offset + 12 <= totalLen) {
            int packetLen = littleEndianInt(payload, offset);
            if (packetLen <= 8) {
                break;
            }

            int fullSize = 4 + packetLen;
            if (fullSize <= 12 || offset + fullSize > totalLen) {
                break;
            }

            int bodyStart = offset + 12;
            int bodyEnd = offset + fullSize;
            int bodyLen = bodyEnd - bodyStart;
            if (bodyLen <= 0) {
                offset += fullSize;
                continue;
            }

            if (payload[bodyEnd - 1] == 0) {
                bodyLen--;
            }

            String text = new String(payload, bodyStart, bodyLen, StandardCharsets.UTF_8);
            Map<String, String> parsed = parseDouyuMessage(text);
            if (!parsed.isEmpty()) {
                result.add(parsed);
            }

            offset += fullSize;
        }

        return result;
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    /**
     * 解析斗鱼 kv 协议：
     *   key@=value/key2@=value2/
     * 转义：@S 表示 /，@A 表示 @。
     */
    private static Map<String, String> parseDouyuMessage(String msg) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (msg == null || msg.length() == 0) {
            return map;
        }

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
                    if (readingKey) {
                        key.append('/');
                    } else {
                        val.append('/');
                    }
                    i++;
                } else if (next == 'A') {
                    if (readingKey) {
                        key.append('@');
                    } else {
                        val.append('@');
                    }
                    i++;
                } else {
                    if (readingKey) {
                        key.append(c);
                    } else {
                        val.append(c);
                    }
                }
            } else {
                if (readingKey) {
                    key.append(c);
                } else {
                    val.append(c);
                }
            }
        }

        if (key.length() > 0) {
            map.put(key.toString(), val.toString());
        }

        return map;
    }

    private static void handleOneMessage(WebSocket webSocket, Map<String, String> parsed) {
        if (parsed == null || parsed.isEmpty()) {
            return;
        }

        String msgType = parsed.get("type");

        if ("pingreq".equals(msgType)) {
            sendDouyuCmd(webSocket, "type@=mrkl/");
            return;
        }

        if (!"defense_tower_session".equals(msgType)) {
            return;
        }

        String sessionId = nullToEmpty(parsed.get("sessionId"));
        String status = nullToEmpty(parsed.get("status"));
        String hitTower = nullToEmpty(parsed.get("hitTower"));
        int leftTime = safeInt(parsed.get("leftTime"), 0);

        if ("2".equals(status) && hitTower.length() == 0) {
            log("开奖消息缺少 hitTower | message=" + parsed);
            return;
        }

        String dedupKey = sessionId + "_" + status;
        if (!dedupCache.addIfNew(dedupKey)) {
            return;
        }

        if ("2".equals(status)) {
            String mappedNumber = mapOpenNumber(hitTower);
            log("开奖 | 期号: " + sessionId
                    + " | 原始值: " + hitTower
                    + " | 映射后: " + mappedNumber
                    + " | 剩余时间: " + leftTime);
            pushDhsgLottery(mappedNumber);
        } else if ("0".equals(status)) {
            long nextOpenMillis = System.currentTimeMillis() + leftTime * 1000L;
            log("下一期开始 | 期号: " + sessionId
                    + " | 倒计时: " + leftTime
                    + " | 预计时间: " + formatTime(nextOpenMillis));
        }
    }

    /**
     * 斗鱼原始号码 -> 大话三国前端号码映射：
     * 1 -> 7；2 -> 6；3 -> 5；4 -> 4；5 -> 3；6 -> 2；7 -> 1
     */
    private static String mapOpenNumber(String hitTower) {
        Map<String, String> mapping = new HashMap<String, String>();
        mapping.put("1", "7");
        mapping.put("2", "6");
        mapping.put("3", "5");
        mapping.put("4", "4");
        mapping.put("5", "3");
        mapping.put("6", "2");
        mapping.put("7", "1");
        String key = String.valueOf(hitTower);
        return mapping.containsKey(key) ? mapping.get(key) : key;
    }

    /**
     * 开奖号码推送到 wanshunGame：POST {url}/gameData/dhsg/luckyMonster  body={"Number": 1~7}。
     * 映射后号码必须落在 1~7，否则跳过（防止把梦想巴士等异常值推上去）。
     */
    private static void pushDhsgLottery(final String mappedNumber) {
        final int openNumber = safeInt(mappedNumber, -1);
        if (openNumber < 1 || openNumber > 7) {
            log("开奖号映射异常，跳过推送 | mappedNumber=" + mappedNumber);
            return;
        }

        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String[] baseUrls = DomainNameUtil.urls;
                if (baseUrls == null || baseUrls.length == 0) {
                    log("未配置 DomainNameUtil.urls，开奖号 " + openNumber + " 已打印但未推送");
                    return;
                }

                JSONObject params = new JSONObject();
                params.put("Number", openNumber);
                String json = params.toJSONString();

                for (String baseUrl : baseUrls) {
                    if (baseUrl == null || baseUrl.trim().length() == 0) {
                        continue;
                    }
                    String url = trimRightSlash(baseUrl.trim()) + "/gameData/dhsg/luckyMonster";
                    sendLotteryHttp(url, json);
                }
            }
        });
    }

    private static void sendLotteryHttp(String url, String json) {
        for (int attempt = 1; attempt <= PUSH_MAX_ATTEMPTS; attempt++) {
            try {
                RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, json);
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String resp = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        log(url + " - 斗鱼-大话三国-开奖结果同步请求响应：" + resp + "（第" + attempt + "次）");
                        return;
                    }
                    log(url + " - 斗鱼-大话三国-开奖结果同步请求异常：HTTP " + response.code()
                            + " | response=" + resp + "（第" + attempt + "次）");
                }
            } catch (Exception e) {
                log(url + " - 斗鱼-大话三国-开奖结果同步请求异常：" + e.getMessage() + "（第" + attempt + "次）");
            }

            if (attempt < PUSH_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(PUSH_RETRY_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log(url + " - 斗鱼-大话三国-开奖结果推送最终失败，已重试 " + PUSH_MAX_ATTEMPTS + " 次 | json=" + json);
    }

    private static String trimRightSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static OkHttpClient buildClient() {
        try {
            X509TrustManager trustManager = trustAllManager();
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{trustManager}, new SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            ConnectionSpec tls12Spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build();

            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .sslSocketFactory(sslSocketFactory, trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .connectionSpecs(Arrays.asList(tls12Spec, ConnectionSpec.CLEARTEXT))
                    .build();
        } catch (Exception e) {
            log("初始化 TLS 客户端失败，降级默认 OkHttpClient | " + e.getMessage());
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
    }

    private static X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static int safeInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nowStr() {
        return formatTime(System.currentTimeMillis());
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
    }

    private static void log(String msg) {
        System.out.println("[" + nowStr() + "] " + msg);
    }

    private static ThreadFactory namedThreadFactory(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(false);
                return thread;
            }
        };
    }

    /**
     * 去重缓存：同一 sessionId + status 仅处理一次。
     */
    private static class DedupCache {
        private final int maxSize;
        private final ArrayDeque<String> queue = new ArrayDeque<String>();
        private final Set<String> set = new HashSet<String>();

        DedupCache(int maxSize) {
            this.maxSize = maxSize;
        }

        synchronized boolean addIfNew(String key) {
            if (key == null || key.length() == 0 || set.contains(key)) {
                return false;
            }
            queue.addLast(key);
            set.add(key);

            while (queue.size() > maxSize) {
                String old = queue.pollFirst();
                if (old != null) {
                    set.remove(old);
                }
            }
            return true;
        }
    }
}
