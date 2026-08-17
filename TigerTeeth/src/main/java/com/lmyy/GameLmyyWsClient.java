package com.lmyy;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.commom.RestTemplateUtils;
import com.utils.DomainNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 浪漫约会<虎牙> WebSocket 客户端
 *
 * @author 哈哈.唐
 * @date 2026-06-08
 */
@ClientEndpoint
public class GameLmyyWsClient {

    private static final Logger logger = LoggerFactory.getLogger(GameLmyyWsClient.class);

    /** 推送重试次数 */
    private static final int PUSH_RETRY = 3;
    /** 推送每次重试间隔 ms */
    private static final long PUSH_RETRY_INTERVAL_MS = 1000L;

    /** 12 个地点的关键字（顺序就是 monsterId 1-12，跟 config_lmyy 一致） */
    private static final List<String> KEYWORDS = Arrays.asList(
            "海边", "极光", "篝火", "露营",
            "音乐节", "游乐园", "海洋馆", "天象馆",
            "咖啡店", "花店", "电影院", "夜市");

    private static final Map<String, Integer> TREASURE_ID_MAP = new LinkedHashMap<>();

    static {
        TREASURE_ID_MAP.put("海边", 1);
        TREASURE_ID_MAP.put("极光", 2);
        TREASURE_ID_MAP.put("篝火", 3);
        TREASURE_ID_MAP.put("露营", 4);
        TREASURE_ID_MAP.put("音乐节", 5);
        TREASURE_ID_MAP.put("游乐园", 6);
        TREASURE_ID_MAP.put("海洋馆", 7);
        TREASURE_ID_MAP.put("天象馆", 8);
        TREASURE_ID_MAP.put("咖啡店", 9);
        TREASURE_ID_MAP.put("花店", 10);
        TREASURE_ID_MAP.put("电影院", 11);
        TREASURE_ID_MAP.put("夜市", 12);
    }

    /** 关键 topic，用来过滤无关消息 */
    private static final String TOPIC_FILTER = "comm:search_treasure_893";

    /** 业务防抖：避免同一关键字 5 秒内多次推送 */
    private static final long DEDUP_WINDOW_MS = 5_000L;

    // ============ 从 HuyaLangmanShenhai.java 原样复制的协议二进制 ============
    private static final String DEFAULT_WS_URL =
            "wss://960595ca-ws.va.huya.com/?baseinfo=AwAAARdOs2iWFiAwYTdkZTI2NTBmYWNjNTY5MjEwMTgzMDg4NDA4ZmQ0NyYi" +
            "d2ViaDUmMC4xLjAmd2Vic29ja2V0JiZkaXloNV81MDcxNTYMSFVZQSZaSCYyMDUyRgBWAGx2AIYAlgCoDA==";

    private static final String SUBSCRIBE_SEARCH_TREASURE_HEX =
            "00101d00001f0900010618636f6d6d3a7365617263685f74726561737572655f38393316002c36004c5c6600";

    private static final String SUBSCRIBE_LIVE_SLOT_HEX =
            "00101d000028090002060e6c6976653a3530303036363137310611636f6d6d3a736c6f745f6d616368696e6516002c36004c" +
            "5c6600";

    private static final String LAUNCH_HEX =
            "00031d0001009d0000009d10032c3c40ff56066c61756e6368660877734c61756e63687d0000780800010604745265711d00" +
            "006b0a03000001174eb368961620306137646532363530666163633536393231303138333038383430386664343726227765" +
            "62683526302e312e3026776562736f636b6574262664697968355f3530373135360c48555941265a4826323035324a060016" +
            "002600360046000b0b8c980ca80c2c36004c5c6600";

    private static final String DEFAULT_AUTH_B64 =
            "AAodAAEIHwMAAAEXUZ/UVhYad2ViaDUmMjYwNjAzMTgwOCZ3ZWJzb2NrZXQnAAAHw19feWFtaWRfbmV3PUNCQjA5MTk0OTdDMDAw" +
            "MDE3MjE2RUY2NUZBNDAxM0UzOyBnYW1lX2RpZD1OR1RmNDJRZWxNdmpDVEtBQVM2cE9Fb3BGWTJqNHpmRVZPUTsgU291bmRWYWx1" +
            "ZT0wLjUwOyBfcWltZWlfdXVpZDQyPTFhNjA3MGMyZDFlMTAwZTUwZjU4YTIyODdlOTE4NjYwY2Q1OTQ4ODY2MzsgX195YW1pZF90" +
            "dDE9MC45MTA0MDkyNDA5MjMxMjI7IGd1aWQ9MGE3ZGZhYTI2OGY3MjQ2YTI2MDE2YjU3NmM1NzYyMDE7IHVkYl9ndWlkZGF0YT1m" +
            "N2UwNWJjMTA4ZmU0NThjYTFlMThmNDhiMzMwMjliNjsgX3FpbWVpX2gzOD1hZWEzYzIxNzBmNThhMjI4N2U5MTg2NjAwMjAwMDAw" +
            "NGExYTYwNzsgdWRiX2RldmljZWlkPXdfMTExNTk4NDk1MzY4MTczOTc3NjsgZ3VpZD0wYTdkZmFhMjY4ZjcyNDZhMjYwMTZiNTc2" +
            "YzU3NjIwMTsgdWRiX2FwcGlkPTUwMDI7IGhkaWQ9OGQ2YzBhZGI5OTYwOGNjN2Y3Yzk0MGY3ZmUxNzU0MzUxNDc1MzQ4YjsgdWRi" +
            "X3Bhc3NkYXRhPTM7IEhtX2x2dF81MTcwMGI2YzcyMmY1YmI0Y2YzOTkwNmE1OTZlYTQxZj0xNzgwODA3NTQwLDE3ODA4MzM4NTQ7" +
            "IEhNQUNDT1VOVD0yMkE2M0UwMjk4RjgyOUFFOyBfX3lhc21pZD0wLjkxMDQwOTI0MDkyMzEyMjsgUEhQU0VTU0lEPTJuZnVsNGI0" +
            "YmJocWs3cGhiNTFvbjlxYXQ3OyBfcWltZWlfZmluZ2VycHJpbnQ9N2MyZTFjYWZjMDEwOGM2M2I0ODkzOWY2ZjQzMTUxZjQ7IGh1" +
            "eWF3YXBfcmVwX2NudD03OyBudWxsX3JlcF9jbnQ9MzsgaF91bnQ9MTc4MDgzNjM4MTsgc2RpZD0wVW5IVWd2MF9xbWZENEtBS2x3" +
            "emhxYTJSaG9hTTNBU0hUZkwwRXVoZzktdzQxTzJqSkw4RzBpNjZ2aUl2U2x2bVB2U3otOWNDaUY0VTNWZUJHdzBObm4yV1NmUUFp" +
            "V3hzMnVnM3BIMFM3c1BXVmtuOUx0ZkZKd19RbzRrZ0tyOE9aSERxTm51d2c2MTJzR3lmbEZuMWRvTE9sbEdIVU5BdEczU0lVeFZH" +
            "Z2tkdGViMlBZMGVLWmxzekoyZ25rLWlHOyB1ZGJfdWlkPTExOTk2NjUzMDQ2NjI7IHl5dWlkPTExOTk2NjUzMDQ2NjI7IHVkYl9w" +
            "YXNzcG9ydD1oeV8yOTgwMzU3MzE7IHVzZXJuYW1lPWh5XzI5ODAzNTczMTsgdWRiX3ZlcnNpb249MS4wOyB1ZGJfYml6dG9rZW49" +
            "QVFBdnVrUEh6X1lOUXdzVllHVHJpYVRrV1J3bXpoZUJSWUxTQ08xdHdTZUdrM2hXM1pIR29GWl9WaXQzZjcwejJKeVp5cy11TXhN" +
            "R0FBX3NuSTFIWENnREt5T0V6V1lPMUV5dFkzaHIxdlZwbmpGVjJ6N0J4aGZXNURDN1ZPVllvQjlLaVNDNWxNbklYY0FhQnV1YnVH" +
            "ejlaUVhEdmgzQWc0a2Z6SVlHbVZ1M3FiWGhZaEJFV1Nwc0pBelJ3TlJ5SXhUV3BKSDVSNFhRV0M4aGJEcHFpUHRhUVFjUDJHcU05" +
            "eWQ0bF9ORzY1dkExajQ3ekN2a25Udng3UmNqaFVOQmgzUThnYWhlYjc4dE9QWldKOWJFWkpjS3Z5SVN6bGtsUW1Qd1BuM0s5bDFK" +
            "QTRCWkxRVTVjYk1DZm5KUDJDX1VBdkdQVFVDTkZhalBvVWV5VzNKZGlVV007IHVkYl9vcmlnaW49MTsgdWRiX3N0YXR1cz0xOyB1" +
            "ZGJfY3JlZD1DbUNpMktRNDl5QmQydTU5bVZfeFR4VXJnQ0ZsVjFjNjlsd2xSVjNOSkZfUW5zZkNyM2JKTEhRM0piY3VIUk1ZSkwt" +
            "a052TmJhWVNUYy1yTzZHaERCaGgwWnFrTy1RdlFsYnhVU2czNW5vbFVZckpnMld6aGotdGFaSmpQQW9pNVEzVVFFdWMxUllhRUNK" +
            "Q3otVkY1RFdZWjsgdWRiX290aGVyPSU3QiUyMmx0JTIyJTNBJTIyMTc4MDgzNjYxNTE3NiUyMiUyQyUyMmlzUmVtJTIyJTNBJTIy" +
            "MSUyMiU3RDsgcmVwX2NudD0xNTsgdWRiX2FjY2RhdGE9MTkzNTQyODAyNjk7IF9feWFvbGR5eXVpZD0xMTk5NjY1MzA0NjYyOyBf" +
            "eWFzaWRzPV9fcm9vdHNpZCUzRENCQjBBRDUzMkNDMDAwMDEyNTQzMUMwMDEwM0YxMjZGOyBhbHBoYVZhbHVlPTAuODA7IGlzSW5M" +
            "aXZlUm9vbT10cnVlOyBodXlhX2ZsYXNoX3JlcF9jbnQ9MTAwOyBIbV9scHZ0XzUxNzAwYjZjNzIyZjViYjRjZjM5OTA2YTU5NmVh" +
            "NDFmPTE3ODA4Mzg2MjA7IGh1eWFfd2ViX3JlcF9jbnQ9MTI2OyBodXlhX3VhPXdlYmg1JjAuMC4xJndlYnNvY2tldCYmaDVfL2th" +
            "bm9uZ2JpYW56b3U2IDBhN2RmYWEyNjhmNzI0NmEyNjAxNmI1NzZjNTc2MjAxQAFWDEhVWUEmWkgmMjA1Miw2AExcZgA=";

    private static final String HEARTBEAT_B64 = "ABQdAAwsNgBMXGYA";

    // ============ runtime state ============
    public final RestTemplateUtils restTemplateUtils;
    private final String wsUrl;
    private final String authB64;

    private Session session;
    private volatile boolean needReconnect = false;
    private volatile long lastOpenMessageTime = System.currentTimeMillis();
    private volatile long lastAnyMessageTime = System.currentTimeMillis();

    /** 最近一次推送的关键字 + 时间戳（业务防抖） */
    private volatile String lastPushedKey = "";
    private volatile long lastPushedTime = 0L;

    public GameLmyyWsClient(RestTemplateUtils restTemplateUtils) {
        this.restTemplateUtils = restTemplateUtils;
        this.wsUrl = envOr("HUYA_LMYY_WS_URL", DEFAULT_WS_URL);
        this.authB64 = envOr("HUYA_LMYY_AUTH_B64", DEFAULT_AUTH_B64);
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    public boolean isNeedReconnect() {
        return needReconnect;
    }

    public void clearReconnectFlag() {
        needReconnect = false;
    }

    public long getMsSinceLastOpenMessage() {
        return System.currentTimeMillis() - lastOpenMessageTime;
    }

    public long getMsSinceLastAnyMessage() {
        return System.currentTimeMillis() - lastAnyMessageTime;
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        logger.info("[浪漫约会<虎牙>] onOpen, 开始发送订阅消息序列");
        try {
            sendB64(authB64, "auth");
            sleepQuietly(300);
            sendHex(SUBSCRIBE_SEARCH_TREASURE_HEX, "subscribe_search_treasure");
            sleepQuietly(300);
            sendHex(SUBSCRIBE_LIVE_SLOT_HEX, "subscribe_live_slot");
            sleepQuietly(300);
            sendHex(LAUNCH_HEX, "launch");
            sleepQuietly(300);
            sendB64(HEARTBEAT_B64, "heartbeat");
            logger.info("[浪漫约会<虎牙>] 订阅消息已全部发送");
        } catch (Exception e) {
            logger.error("[浪漫约会<虎牙>] onOpen 发送订阅消息异常", e);
            this.session = null;
            needReconnect = true;
        }
    }

    @OnMessage
    public void pongMessage(Session session, PongMessage msg) {
        logger.info("[浪漫约会<虎牙>] 收到 PongMessage: {}", msg);
    }

    @OnMessage
    public void textMessage(Session session, String message) {
        lastAnyMessageTime = System.currentTimeMillis();
        handleText(message);
    }

    @OnMessage
    public void binaryMessage(Session session, ByteBuffer msg) {
        lastAnyMessageTime = System.currentTimeMillis();
        byte[] data = new byte[msg.remaining()];
        msg.get(data);
        String text = decodeUtf8Ignore(data);
        handleText(text);
    }

    private void handleText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!text.contains(TOPIC_FILTER)) {
            return;
        }

        // 匹配到 search_treasure topic，做关键字识别
        List<String> matched = new ArrayList<>();
        for (String word : KEYWORDS) {
            if (text.contains(word)) {
                matched.add(word);
            }
        }
        if (matched.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (matched.size() == 1) {
            // 单关键字 = 开奖
            String name = matched.get(0);
            Integer monsterId = TREASURE_ID_MAP.get(name);
            if (monsterId == null) {
                logger.warn("[浪漫约会<虎牙>] 未识别的关键字: {}", name);
                return;
            }
            // 业务防抖：同一关键字 5s 内不重复推
            synchronized (this) {
                if (name.equals(lastPushedKey) && (now - lastPushedTime) < DEDUP_WINDOW_MS) {
                    logger.info("[浪漫约会<虎牙>] 防抖跳过重复开奖：{} (距离上次 {}ms)", name, now - lastPushedTime);
                    return;
                }
                lastPushedKey = name;
                lastPushedTime = now;
            }
            lastOpenMessageTime = now;
            logger.info("[浪漫约会<虎牙>] 开奖 monsterId={} name={}", monsterId, name);

            JSONObject body = new JSONObject();
            body.set("monsterId", monsterId);
            body.set("monsterName", name);
            body.set("lRoundId", now);
            body.set("lServerTime", now);
            body.set("lRoundIndexEndTime", now);
            body.set("lRoundIndexTime", now - 135_000);
            final String bodyStr = body.toString();

            for (String url : DomainNameUtil.urls) {
                final String pushUrl = url + "/lmyy/luckyMonster";
                CompletableFuture.runAsync(() -> postWithRetry(pushUrl, bodyStr, "虎牙-浪漫约会-开奖"));
            }
        } else {
            // 多关键字 = 新一局开始（用户约定：下注 120s + 开奖 15s = 135s/期）
            // 业务防抖：单局 60s 内不重复触发开盘（小于一期总长 135s，留缓冲）
            synchronized (this) {
                if ((now - lastPushedTime) < 60_000L && "__coming__".equals(lastPushedKey)) {
                    return;
                }
                lastPushedKey = "__coming__";
                lastPushedTime = now;
            }
            // 推给 wanshunGame 的开奖时间 = 当前 + 120s（下注期长度）
            long lRoundIndexEndTime = now + 120_000;
            logger.info("[浪漫约会<虎牙>] 新一局开始，下注倒计时 120s，关键字={}, 预计开奖时间={}", matched, lRoundIndexEndTime);

            JSONObject body = new JSONObject();
            body.set("lRoundIndexEndTime", lRoundIndexEndTime);
            final String bodyStr = body.toString();

            // 通知 wanshunGame 当期开盘
            for (String url : DomainNameUtil.urls) {
                final String pushUrl = url + "/lmyy/coming";
                CompletableFuture.runAsync(() -> postWithRetry(pushUrl, bodyStr, "虎牙-浪漫约会-开盘"));
            }

            // 同步给中转服务（兼容现有 gameProxy 接口）
            for (String url : DomainNameUtil.transitUrls) {
                final String setTimeUrl = url + "/gameProxy/proxy/setGameTime?time=" + lRoundIndexEndTime + "&gameId=38";
                CompletableFuture.runAsync(() -> getWithLog(setTimeUrl, "虎牙-浪漫约会-同步开盘时间"));
            }
        }
    }

    @OnClose
    public void onClose() {
        logger.error("[浪漫约会<虎牙>] 链接关闭，标记立即重连");
        session = null;
        needReconnect = true;
    }

    @OnError
    public void onError(Throwable e, Session session) {
        logger.error("[浪漫约会<虎牙>] 监听到异常，标记立即重连", e);
        this.session = null;
        needReconnect = true;
    }

    /** 守护线程检测到订阅疑似失效时调用，强制关闭 session 触发重连 */
    public synchronized void forceReconnect(String reason) {
        logger.warn("[浪漫约会<虎牙>] 主动强制重连，原因: {}", reason);
        try {
            if (session != null) {
                session.close();
            }
        } catch (IOException ignored) {
        }
        session = null;
        needReconnect = true;
        lastOpenMessageTime = System.currentTimeMillis();
        lastAnyMessageTime = System.currentTimeMillis();
    }

    private synchronized void connect() {
        try {
            logger.info("[浪漫约会<虎牙>] 准备连接 => {}", wsUrl);
            WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
            webSocketContainer.setDefaultMaxTextMessageBufferSize(65536);
            webSocketContainer.setDefaultMaxBinaryMessageBufferSize(65536);
            webSocketContainer.setDefaultMaxSessionIdleTimeout(30000);
            webSocketContainer.setAsyncSendTimeout(20000);

            session = webSocketContainer.connectToServer(this, new URI(wsUrl));
        } catch (Exception e) {
            logger.error("[浪漫约会<虎牙>] {} 链接失败", wsUrl, e);
        }
    }

    /** 守护线程每隔 5s 调一次，没连上就 connect()，连上了就发心跳 */
    public synchronized void report() {
        if (session == null || !session.isOpen()) {
            logger.info("[浪漫约会<虎牙>] session 不可用，进行重连");
            connect();
            return;
        }
        try {
            sendB64(HEARTBEAT_B64, "heartbeat");
        } catch (Exception e) {
            logger.error("[浪漫约会<虎牙>] 心跳发送异常，准备重连", e);
            session = null;
            needReconnect = true;
        }
    }

    private void sendHex(String hex, String name) {
        byte[] data = hexToBytes(hex);
        session.getAsyncRemote().sendBinary(ByteBuffer.wrap(data));
    }

    private void sendB64(String b64, String name) {
        byte[] data = Base64.getDecoder().decode(b64);
        session.getAsyncRemote().sendBinary(ByteBuffer.wrap(data));
    }

    private static byte[] hexToBytes(String hex) {
        String clean = hex.trim();
        if ((clean.length() & 1) != 0) {
            throw new IllegalArgumentException("hex length must be even");
        }
        byte[] data = new byte[clean.length() / 2];
        for (int i = 0; i < clean.length(); i += 2) {
            int high = Character.digit(clean.charAt(i), 16);
            int low = Character.digit(clean.charAt(i + 1), 16);
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }

    private static String decodeUtf8Ignore(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return "";
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 同步重试推送（在 CompletableFuture 内部调用） */
    private void postWithRetry(String url, String body, String tag) {
        Exception lastEx = null;
        for (int i = 1; i <= PUSH_RETRY; i++) {
            try {
                ResponseEntity<String> responseEntity = restTemplateUtils.post(url, body, String.class);
                String resp = responseEntity.getBody();
                if (i == 1) {
                    logger.info("[{}] 推送成功 url={} resp={}", tag, url, resp);
                } else {
                    logger.warn("[{}] 重试第 {}/{} 次推送成功 url={} resp={}", tag, i, PUSH_RETRY, url, resp);
                }
                return;
            } catch (RestClientException e) {
                lastEx = e;
                logger.warn("[{}] 推送失败 第{}/{}次 url={} err={}", tag, i, PUSH_RETRY, url, e.getMessage());
            } catch (Exception e) {
                lastEx = e;
                logger.warn("[{}] 推送异常 第{}/{}次 url={} err={}", tag, i, PUSH_RETRY, url, e.getMessage());
            }
            if (i < PUSH_RETRY) {
                try {
                    Thread.sleep(PUSH_RETRY_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        logger.error("[{}] 推送最终失败({}次重试用尽) url={} lastErr={}", tag, PUSH_RETRY, url,
                lastEx == null ? "null" : lastEx.getMessage());
    }

    private void getWithLog(String url, String tag) {
        try {
            ResponseEntity<String> responseEntity = restTemplateUtils.get(url, String.class);
            String resp = responseEntity.getBody();
            logger.info("[{}] 同步成功 url={} resp={}", tag, url, resp);
        } catch (RestClientException e) {
            logger.warn("[{}] 同步异常 url={} err={}", tag, url, e.getMessage());
        } catch (Exception e) {
            logger.error("[{}] 同步异常 url={}", tag, url, e);
        }
    }
}
