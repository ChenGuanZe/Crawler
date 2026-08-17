package com.shmj;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.commom.RestTemplateUtils;
import com.utils.DomainNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * 深海迷境 SHMJ <来连接/恩亨> WebSocket 客户端
 *
 * <p>协议来源：业务方提供的 XxcdCrawler.java（来连接 lailianjie 平台）。</p>
 * <p>本类完成：getTempKey(HTTP 签名) → enterGame(取 wsUrl/token) → c_login → 处理 r_beginBet/r_cleanUp/r_trends，
 * 把开奖号码 push 到 wanshunGame、把下次开奖时刻 push 到 gameProxy。</p>
 *
 * <p>下游协议约定：</p>
 * <ul>
 *   <li>POST {wanshunGame}/ljhdshmj/luckyMonster body={"code":1,"id":monsterId} —— 由 ShmjCache.monsterMap 消费</li>
 *   <li>GET  {gameProxy}/gameProxy/proxy/setGameTime?gameId=35&time=openTime —— ShmjRoom.getGameInfo 拉</li>
 * </ul>
 *
 * @author 哈哈.唐
 * @date 2026-06-09
 */
@ClientEndpoint
public class GameShmjWsClient {

    private static final Logger logger = LoggerFactory.getLogger(GameShmjWsClient.class);

    private static final int GAME_ID = 35;
    private static final String GAME_TAG = "<来连接>深海迷境";

    /** 推送重试次数 */
    private static final int PUSH_RETRY = 3;
    /** 推送每次重试间隔 ms */
    private static final long PUSH_RETRY_INTERVAL_MS = 1000L;

    /** rid → monsterId（1-8） → 鱼名 */
    private static final Map<Integer, String> RESULT_NAME_MAP = new HashMap<>();

    static {
        RESULT_NAME_MAP.put(1, "比目鱼");
        RESULT_NAME_MAP.put(2, "小丑鱼");
        RESULT_NAME_MAP.put(3, "石斑鱼");
        RESULT_NAME_MAP.put(4, "河豚");
        RESULT_NAME_MAP.put(5, "大黄鱼");
        RESULT_NAME_MAP.put(6, "大章鱼");
        RESULT_NAME_MAP.put(7, "鲨鱼");
        RESULT_NAME_MAP.put(8, "鲸鱼");
    }

    private static final Random RANDOM = new SecureRandom();

    // ============ lailianjie / engheng 平台配置（允许通过环境变量覆盖） ============
    private static final String CFG_LOGIN_TOKEN = envOr("SHMJ_LOGIN_TOKEN", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhIjoiMjcyNzI2MyIsImIiOiIxODU5NTY1NzI2NSIsImYiOiJjZHV0ZHh4anR0MHgwZGE4YTBjeG94YThrbmxmMmdxbiIsImQiOjE3ODM5MTA5MTgsImUiOiJBQUHmmJ_msrMiLCJjIjoxMDAyNjcxOCwiaCI6InBjIiwiZyI6Im1rdmhoaHpqYW5xMCIsImV4cCI6MTc4NjUwMjkxOH0.6FFGrXYG7bFMZPg9BgYGn0d9NsccidWjJQqiYvyg8yA");
    private static final String CFG_UID = envOr("SHMJ_UID", "2727263");
    private static final String CFG_RID = envOr("SHMJ_RID", "881013");
    private static final long CFG_UUID = parseLongSafe(envOr("SHMJ_UUID", "-1458690917"), -1458690917L);
    private static final String CFG_BASE_URL = envOr("SHMJ_BASE_URL", "https://api.lailianjie.com");
    private static final String CFG_TEMP_KEY_PATH = envOr("SHMJ_TEMP_KEY_PATH", "/ApiServices/third/game/getTempKey");
    private static final String CFG_ENTER_GAME_BASE = envOr("SHMJ_ENTER_GAME_BASE", "https://login-nl.engheng.com");
    private static final String CFG_ENTER_GAME_PATH = envOr("SHMJ_ENTER_GAME_PATH", "/xxcd/enterGame");
    private static final int CFG_REQUEST_TIMEOUT_MS = parseIntSafe(envOr("SHMJ_REQUEST_TIMEOUT_MS", "60000"), 60000);

    /** 业务防抖：setGameTime 5 秒内只推一次（r_beginBet 可能频繁推送） */
    private static final long SET_GAME_TIME_DEDUP_MS = 5_000L;
    /** 业务防抖：同一 monsterId 5 秒内不重复推 luckyMonster（避免 r_trends 重复） */
    private static final long LUCKY_PUSH_DEDUP_MS = 5_000L;

    static {
        // 跟 XxcdCrawler 一样信任全部证书（lailianjie/engheng 的证书在容器内可能没有 CA）
        try {
            installTrustAllSsl();
        } catch (Exception e) {
            logger.warn("[{}] 安装 TrustAllSsl 失败（仅影响 HTTPS 调用，HTTP 不影响）", GAME_TAG, e);
        }
    }

    private final RestTemplateUtils restTemplateUtils;
    private volatile String wsToken;
    private volatile Session session;
    private volatile boolean needReconnect = false;
    private volatile long lastOpenMessageTime = System.currentTimeMillis();
    private volatile long lastAnyMessageTime = System.currentTimeMillis();

    /** 防抖状态 */
    private volatile long lastSetGameTimeAt = 0L;
    private volatile int lastPushedMonsterId = 0;
    private volatile long lastPushedAt = 0L;

    public GameShmjWsClient(RestTemplateUtils restTemplateUtils) {
        this.restTemplateUtils = restTemplateUtils;
    }

    // ============ 守护接口（DwydhService 调用） ============

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

    public synchronized void forceReconnect(String reason) {
        logger.warn("[{}] 主动强制重连，原因: {}", GAME_TAG, reason);
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

    /**
     * 守护线程每 5s 调一次：没连上就发起 connect；连上了就发个 c_t_t 当心跳。
     */
    public synchronized void report() {
        if (session == null || !session.isOpen()) {
            logger.info("[{}] session 不可用，进行重连", GAME_TAG);
            connect();
            return;
        }
        try {
            sendTextSafe(buildTMessage());
        } catch (Exception e) {
            logger.error("[{}] 心跳发送异常，准备重连", GAME_TAG, e);
            session = null;
            needReconnect = true;
        }
    }

    // ============ WS 生命周期 ============

    private synchronized void connect() {
        try {
            if (isBlank(CFG_LOGIN_TOKEN)) {
                logger.error("[{}] 环境变量 SHMJ_LOGIN_TOKEN 未配置，跳过连接（请联系业务方提供来连接平台的登录 token）", GAME_TAG);
                return;
            }
            logger.info("[{}] 准备连接流程，baseUrl={} enterGame={} uid={} rid={}",
                    GAME_TAG, CFG_BASE_URL, CFG_ENTER_GAME_BASE + CFG_ENTER_GAME_PATH, CFG_UID, CFG_RID);

            String code = getTempKey();
            WsInfo wsInfo = getWsInfo(code);
            this.wsToken = wsInfo.wsToken;
            logger.info("[{}] 解析得到 wsUrl={}", GAME_TAG, wsInfo.socketUrl);

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(65536);
            container.setDefaultMaxBinaryMessageBufferSize(65536);
            container.setDefaultMaxSessionIdleTimeout(60_000);
            container.setAsyncSendTimeout(20_000);
            session = container.connectToServer(this, new URI(wsInfo.socketUrl));
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("账号异地登录") || msg.contains("-100007")) {
                logger.error("[{}] 登录态已失效（账号异地登录或 -100007），请重新获取 loginToken: {}", GAME_TAG, msg);
            } else {
                logger.error("[{}] connect 异常: {}", GAME_TAG, msg, e);
            }
            session = null;
            needReconnect = true;
        }
    }

    @OnOpen
    public void onOpen(Session sess) {
        this.session = sess;
        logger.info("[{}] onOpen，发送 c_login", GAME_TAG);
        try {
            String loginMsg = "{\"name\":\"c_login\",\"data\":{\"token\":" + jsonQuote(wsToken) + ",\"v\":\"v10\",\"type\":2}}";
            session.getBasicRemote().sendText(loginMsg);
        } catch (Exception e) {
            logger.error("[{}] 发送 c_login 异常", GAME_TAG, e);
            session = null;
            needReconnect = true;
        }
    }

    @OnMessage
    public void textMessage(Session sess, String message) {
        lastAnyMessageTime = System.currentTimeMillis();
        handleText(message);
    }

    @OnClose
    public void onClose() {
        logger.warn("[{}] 链接关闭，标记立即重连", GAME_TAG);
        session = null;
        needReconnect = true;
    }

    @OnError
    public void onError(Throwable e, Session sess) {
        logger.error("[{}] 监听到异常，标记立即重连", GAME_TAG, e);
        this.session = null;
        needReconnect = true;
    }

    // ============ 业务消息处理 ============

    private void handleText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        JSONObject obj;
        try {
            obj = JSONUtil.parseObj(text);
        } catch (Exception ignored) {
            return;
        }
        String name = obj.getStr("name");
        if (name == null) {
            return;
        }
        switch (name) {
            case "r_roomMsg":
                sendTextSafe(buildTMessage());
                break;
            case "r_beginBet":
                handleBeginBet(obj);
                break;
            case "r_cleanUp":
                handleCleanUp(obj);
                break;
            case "r_trends":
                handleTrends(obj);
                break;
            default:
                // 其余消息忽略
        }
    }

    /** 倒计时：本期下注期开始，cd 秒后开奖 → 推 setGameTime(openTime) 给 gameProxy */
    private void handleBeginBet(JSONObject obj) {
        JSONObject data = obj.getJSONObject("data");
        if (data == null) {
            return;
        }
        Object cdRaw = data.get("cd");
        Object maxCdRaw = data.get("maxCd");
        if (cdRaw == null) {
            return;
        }
        long cd;
        try {
            cd = Long.parseLong(String.valueOf(cdRaw));
        } catch (Exception e) {
            return;
        }
        long cdMs = cd >= 1000 ? cd : cd * 1000L;
        long now = System.currentTimeMillis();

        if (now - lastSetGameTimeAt < SET_GAME_TIME_DEDUP_MS) {
            return;
        }
        lastSetGameTimeAt = now;
        lastOpenMessageTime = now;

        long openTime = now + cdMs;
        logger.info("[{}] r_beginBet cd={} maxCd={} → 计算开奖时刻 {}", GAME_TAG, cdRaw, maxCdRaw, openTime);

        for (String url : DomainNameUtil.transitUrls) {
            final String setTimeUrl = url + "/gameProxy/proxy/setGameTime?gameId=" + GAME_ID + "&time=" + openTime;
            CompletableFuture.runAsync(() -> getWithLog(setTimeUrl, GAME_TAG + "-setGameTime"));
        }
    }

    /** 结算阶段开始：主动发 c_trends 询问最新开奖 */
    private void handleCleanUp(JSONObject obj) {
        JSONObject data = obj.getJSONObject("data");
        Object issue = data == null ? null : data.get("issue");
        logger.info("[{}] r_cleanUp issue={}，发送 c_trends 询问最新开奖", GAME_TAG, issue);
        sendTextSafe("{\"name\":\"c_trends\",\"data\":{\"type\":4}}");
    }

    /** 开奖结果：trends 数组末位是最新一期 → 推 {"code":1,"id":N} 到 wanshunGame */
    private void handleTrends(JSONObject obj) {
        JSONObject data = obj.getJSONObject("data");
        if (data == null) {
            return;
        }
        Object trendsRaw = data.get("trends");
        if (!(trendsRaw instanceof JSONArray)) {
            logger.warn("[{}] r_trends trends 不是数组：{}", GAME_TAG, trendsRaw);
            return;
        }
        JSONArray trends = (JSONArray) trendsRaw;
        if (trends.size() == 0) {
            logger.info("[{}] r_trends trends 为空", GAME_TAG);
            return;
        }
        Object latestRaw = trends.get(trends.size() - 1);
        int monsterId;
        try {
            monsterId = Integer.parseInt(String.valueOf(latestRaw));
        } catch (Exception e) {
            logger.warn("[{}] 无法解析最新开奖号: {}", GAME_TAG, latestRaw);
            return;
        }
        if (monsterId < 1 || monsterId > 8) {
            logger.warn("[{}] 开奖号 {} 不在 1-8 范围", GAME_TAG, monsterId);
            return;
        }
        String fishName = RESULT_NAME_MAP.get(monsterId);

        long now = System.currentTimeMillis();
        synchronized (this) {
            if (monsterId == lastPushedMonsterId && (now - lastPushedAt) < LUCKY_PUSH_DEDUP_MS) {
                logger.info("[{}] 防抖跳过重复开奖：{} ({})", GAME_TAG, monsterId, fishName);
                return;
            }
            lastPushedMonsterId = monsterId;
            lastPushedAt = now;
        }
        lastOpenMessageTime = now;
        logger.info("[{}] 开奖 monsterId={} name={}", GAME_TAG, monsterId, fishName);

        // ShmjRoom 期望 JSON 体：{ code: 1, id: monsterId }
        JSONObject body = new JSONObject();
        body.set("code", 1);
        body.set("id", monsterId);
        body.set("name", fishName);
        body.set("ts", now);
        final String bodyStr = body.toString();

        for (String url : DomainNameUtil.urls) {
            final String pushUrl = url + "/ljhdshmj/luckyMonster";
            CompletableFuture.runAsync(() -> postWithRetry(pushUrl, bodyStr, GAME_TAG + "-开奖"));
        }
    }

    // ============ lailianjie HTTP 调用：getTempKey + enterGame ============

    private static String getTempKey() throws Exception {
        long secTime = System.currentTimeMillis() / 1000L;
        long msTime = System.currentTimeMillis();

        // 与 XxcdCrawler 完全一致的签名字符串（注意不要修改顺序，否则签名失败）
        String jsString =
                "{\"transitional\":{\"silentJSONParsing\":true,\"forcedJSONParsing\":true,\"clarifyTimeoutError\":false},"
                        + "\"adapter\":[\"xhr\",\"http\",\"fetch\"],"
                        + "\"transformRequest\":[null],"
                        + "\"transformResponse\":[null],"
                        + "\"timeout\":10000,"
                        + "\"xsrfCookieName\":\"XSRF-TOKEN\","
                        + "\"xsrfHeaderName\":\"X-XSRF-TOKEN\","
                        + "\"maxContentLength\":-1,"
                        + "\"maxBodyLength\":-1,"
                        + "\"env\":{},"
                        + "\"headers\":{"
                        + "\"uuid\":" + CFG_UUID + ","
                        + "\"os\":\"3\","
                        + "\"version\":1,"
                        + "\"channel\":0,"
                        + "\"gpx_x\":0,"
                        + "\"gpx_y\":0,"
                        + "\"time\":" + secTime + ","
                        + "\"token\":\"" + CFG_LOGIN_TOKEN + "\","
                        + "\"Accept\":\"application/json, text/plain, */*\","
                        + "\"Content-Type\":\"application/json;charset=UTF-8\""
                        + "},"
                        + "\"data\":{},"
                        + "\"method\":\"post\","
                        + "\"url\":\"" + CFG_TEMP_KEY_PATH + "?&_$t=" + msTime + "\","
                        + "\"baseURL\":\"" + CFG_BASE_URL + "\""
                        + "}";

        String sign = md5(jsString);
        String signedUrl = CFG_BASE_URL + CFG_TEMP_KEY_PATH + "?&_$t=" + msTime + "&_sign=" + sign;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("uuid", String.valueOf(CFG_UUID));
        headers.put("os", "3");
        headers.put("version", "1");
        headers.put("channel", "0");
        headers.put("gpx_x", "0");
        headers.put("gpx_y", "0");
        headers.put("time", String.valueOf(secTime));
        headers.put("token", CFG_LOGIN_TOKEN);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("User-Agent", "Mozilla/5.0");

        String body = httpPostJson(signedUrl, headers, "{}");
        JSONObject obj = JSONUtil.parseObj(body);
        Integer code = obj.getInt("code");
        if (code == null || code != 100000) {
            throw new RuntimeException("getTempKey 失败: " + body);
        }
        Object data = obj.get("data");
        if (data == null) {
            throw new RuntimeException("getTempKey 返回 data 为空: " + body);
        }
        return String.valueOf(data);
    }

    private static WsInfo getWsInfo(String code) throws Exception {
        String url = CFG_ENTER_GAME_BASE + CFG_ENTER_GAME_PATH
                + "?code=" + URLEncoder.encode(code, "UTF-8")
                + "&uid=" + URLEncoder.encode(CFG_UID, "UTF-8")
                + "&rid=" + URLEncoder.encode(CFG_RID, "UTF-8");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CFG_REQUEST_TIMEOUT_MS);
            conn.setReadTimeout(CFG_REQUEST_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int status = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            if (location == null || location.length() == 0) {
                String respBody = readResponseBody(conn);
                throw new RuntimeException("enterGame 失败: status=" + status + ", body=" + respBody);
            }
            URI uri = new URI(location);
            Map<String, String> q = parseQuery(uri.getRawQuery());
            String socketUrl = q.get("socketUrl");
            String wsToken = q.get("token");
            if (isBlank(socketUrl) || isBlank(wsToken)) {
                throw new RuntimeException("Location 解析失败: " + location);
            }
            return new WsInfo(socketUrl, wsToken);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ============ HTTP 推送给 wanshunGame / gameProxy ============

    /** 同步重试 POST（在 CompletableFuture 内部调用） */
    private void postWithRetry(String url, String body, String tag) {
        Exception lastEx = null;
        for (int i = 1; i <= PUSH_RETRY; i++) {
            try {
                ResponseEntity<String> resp = restTemplateUtils.post(url, body, String.class);
                String text = resp.getBody();
                if (i == 1) {
                    logger.info("[{}] 推送成功 url={} resp={}", tag, url, text);
                } else {
                    logger.warn("[{}] 重试第 {}/{} 次推送成功 url={} resp={}", tag, i, PUSH_RETRY, url, text);
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
            ResponseEntity<String> resp = restTemplateUtils.get(url, String.class);
            logger.info("[{}] 同步成功 url={} resp={}", tag, url, resp.getBody());
        } catch (RestClientException e) {
            logger.warn("[{}] 同步异常 url={} err={}", tag, url, e.getMessage());
        } catch (Exception e) {
            logger.error("[{}] 同步异常 url={}", tag, url, e);
        }
    }

    // ============ WS 文本发送 ============

    private void sendTextSafe(String text) {
        Session s = session;
        if (s == null || !s.isOpen()) {
            return;
        }
        try {
            s.getAsyncRemote().sendText(text);
        } catch (Exception e) {
            logger.warn("[{}] sendText 异常: {}", GAME_TAG, e.getMessage());
        }
    }

    private static String buildTMessage() {
        return "{\"name\":\"c_t_t\",\"data\":{\"rName1\":" + jsonQuote(randStr(16))
                + ",\"rName2\":" + jsonQuote(randStr(16))
                + ",\"time\":" + System.currentTimeMillis()
                + ",\"type\":2}}";
    }

    // ============ HTTP 通用工具 ============

    private static String httpPostJson(String url, Map<String, String> headers, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CFG_REQUEST_TIMEOUT_MS);
            conn.setReadTimeout(CFG_REQUEST_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bodyBytes);
            }
            int status = conn.getResponseCode();
            String respBody = readResponseBody(conn);
            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP POST 失败: status=" + status + ", body=" + respBody);
            }
            return respBody;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponseBody(HttpURLConnection conn) throws IOException {
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            in = conn.getErrorStream();
        }
        if (in == null) {
            return "";
        }
        try (InputStream stream = in) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) >= 0) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.length() == 0) {
            return map;
        }
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            String key;
            String value;
            if (idx >= 0) {
                key = part.substring(0, idx);
                value = part.substring(idx + 1);
            } else {
                key = part;
                value = "";
            }
            map.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return map;
    }

    private static String md5(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static String jsonQuote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String randStr(int n) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLongSafe(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static void installTrustAllSsl() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
    }

    private static class WsInfo {
        final String socketUrl;
        final String wsToken;

        WsInfo(String socketUrl, String wsToken) {
            this.socketUrl = socketUrl;
            this.wsToken = wsToken;
        }
    }
}
