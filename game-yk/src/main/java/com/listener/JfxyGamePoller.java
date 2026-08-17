package com.listener;

import com.utils.DomainNameUtil;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 九法降妖<酷狗> 采集器。
 * <p>
 * 与用户验证可跑通的 KougouShenhai 脚本【同一套技术栈】：HttpURLConnection + Nashorn(JDK8自带) 解析 JSON，
 * 不依赖 okhttp/jackson，避免依赖冲突导致线程静默退出。区别仅在于：原脚本把结果 println/存CSV，
 * 这里改为推送到游戏服 POST /jfxy/luckyMonster + setGameTime。
 * <p>
 * 一局 50s：投注35s + 等待5s + 开奖动画10s。开奖号按 AWARD_ID_MAP 映射，未命中原值透传。
 */
public class JfxyGamePoller {

    private static final int GAME_ID = 39;

    private static final String MAIN_PAGE_URL = "https://mapi.tencentmusic.com/kugou/spell_tamer.SpellTamerApi/MainPage";
    private static final String ROUND_AWARD_URL = "https://mapi.tencentmusic.com/kugou/spell_tamer.SpellTamerApi/RoundAward";

    private static final int ACTIVITY_ID = 2;
    private static final String ROOM_ID = "2678075";

    private static final long ROUND_STEP_MS = 50001L;
    private static final long ROUND_DRAW_OFFSET_MS = 40000L;
    private static final long REQUEST_DELAY_AFTER_DRAW_MS = 300L;
    private static final int MAX_RETRY = 15;
    private static final long RETRY_SLEEP_MS = 350L;
    private static final int SYNC_EVERY_ROUNDS = 3;
    private static final long MAX_BEHIND_MS = 3000L;
    private static final long AUTH_RECHECK_SLEEP_MS = 20000L;

    private static final String AUTH_APP_ID = "1000756";
    private static final String AUTH_TOKEN_TYPE = "1010";
    private static final String AUTH_UID = "678244938";
    private static final String FALLBACK_TICKET = "a79cb5a053ecd5f0ed0edaf7fabc73e4e32767a0eb4d86a2f0e873066be69645";

    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    /** 官方开奖ID -> 我方ID；未命中原值透传（与 KougouShenhai 一致） */
    private static final Map<Integer, Integer> AWARD_ID_MAP = new HashMap<Integer, Integer>();

    static {
        AWARD_ID_MAP.put(1, 6);
        AWARD_ID_MAP.put(4, 3);
        AWARD_ID_MAP.put(6, 4);
        AWARD_ID_MAP.put(9, 5);
        AWARD_ID_MAP.put(5, 1);
        AWARD_ID_MAP.put(3, 9);
    }

    private boolean stop = false;
    private ScriptEngine jsonEngine;

    /** 常驻入口（在独立线程里调用，内部死循环） */
    public void start() {
        log("九法降妖<酷狗> 采集启动 roomId=" + ROOM_ID + " activityId=" + ACTIVITY_ID + " 映射=" + AWARD_ID_MAP);

        jsonEngine = new ScriptEngineManager().getEngineByName("javascript");
        if (jsonEngine == null) {
            log("【致命】当前 Java 无 javascript/Nashorn 引擎，请用 JDK8 运行。version=" + System.getProperty("java.version"));
            return;
        }

        MainInfo mainInfo = safeRequestMainPageUntilOk();
        if (mainInfo == null) {
            log("九法降妖<酷狗> 初始化失败，采集退出");
            return;
        }

        long currentRoundId = mainInfo.roundId;
        long serverOffsetMs = mainInfo.serverOffsetMs;
        int roundCount = 0;

        while (!stop) {
            try {
                String result = collectOneRound(currentRoundId, serverOffsetMs);

                if ("AUTH_FAILED".equals(result)) {
                    MainInfo recovered = waitAuthRecover();
                    if (recovered == null) {
                        return;
                    }
                    currentRoundId = recovered.roundId;
                    serverOffsetMs = recovered.serverOffsetMs;
                    roundCount = 0;
                    continue;
                }

                roundCount++;
                long nextRoundId = currentRoundId + ROUND_STEP_MS;

                boolean needSync = (roundCount % SYNC_EVERY_ROUNDS == 0) || shouldForceSync(nextRoundId, serverOffsetMs);
                if (needSync) {
                    MainPageResult sync = requestMainPage();
                    if ("OK".equals(sync.status) && sync.info != null) {
                        serverOffsetMs = sync.info.serverOffsetMs;
                        if (sync.info.roundId > nextRoundId) {
                            nextRoundId = sync.info.roundId;
                        }
                    } else if ("AUTH_FAILED".equals(sync.status)) {
                        MainInfo recovered = waitAuthRecover();
                        if (recovered == null) {
                            return;
                        }
                        nextRoundId = recovered.roundId;
                        serverOffsetMs = recovered.serverOffsetMs;
                        roundCount = 0;
                    }
                }

                currentRoundId = nextRoundId;
            } catch (Throwable t) {
                log("九法降妖<酷狗> 主循环异常: " + t);
                t.printStackTrace();
                sleepQuietly(3000L);
            }
        }
    }

    // ============================================================
    // 采集一期 + 推送
    // ============================================================

    private String collectOneRound(long roundId, long serverOffsetMs) {
        long drawTime = roundId + ROUND_DRAW_OFFSET_MS;
        long requestTime = drawTime + REQUEST_DELAY_AFTER_DRAW_MS;

        waitUntilServerTime(requestTime, serverOffsetMs);
        if (stop) {
            return "STOP";
        }

        for (int retry = 1; retry <= MAX_RETRY; retry++) {
            if (stop) {
                return "STOP";
            }
            try {
                PostResult post = requestRoundAward(roundId);
                if (isAuthFailed(post)) {
                    log("九法降妖<酷狗> RoundAward token 疑似失效: " + post.rawText);
                    return "AUTH_FAILED";
                }
                if (post.statusCode != 200) {
                    sleepQuietly(RETRY_SLEEP_MS);
                    continue;
                }
                Map<?, ?> award = extractAward(post.data);
                if (award != null) {
                    int ourId = normalizeToOurId(award);
                    log("九法降妖<酷狗> 开奖 roundId=" + roundId + " ourId=" + ourId + " award=" + award);
                    if (ourId >= 1 && ourId <= 9) {
                        pushLottery(ourId);
                    } else {
                        log("九法降妖<酷狗> 开奖号映射失败，跳过本期 roundId=" + roundId);
                    }
                    pushGameTime();
                    return "OK";
                }
                sleepQuietly(RETRY_SLEEP_MS);
            } catch (Exception e) {
                log("九法降妖<酷狗> RoundAward异常 roundId=" + roundId + " retry=" + retry + " err=" + e.getMessage());
                sleepQuietly(RETRY_SLEEP_MS);
            }
        }
        log("九法降妖<酷狗> 本期多次重试失败 roundId=" + roundId);
        pushGameTime();
        return "FAILED";
    }

    private int normalizeToOurId(Map<?, ?> award) {
        Object rawObj = firstNonNull(award.get("beastId"), award.get("id"));
        Integer rawId = toIntegerOrNull(rawObj);
        if (rawId == null) {
            return -1;
        }
        Integer mapped = AWARD_ID_MAP.get(rawId);
        return mapped == null ? rawId : mapped;
    }

    /** 推送开奖号：{"monsterId":<1-9>,"code":1} -> wanshunGame /jfxy/luckyMonster */
    private void pushLottery(int ourId) {
        String json = "{\"monsterId\":" + ourId + ",\"code\":1}";
        for (String url : DomainNameUtil.urls) {
            try {
                String resp = httpPostJson(url + "/jfxy/luckyMonster", json);
                log("九法降妖<酷狗> 推送开奖 " + url + " => " + resp);
            } catch (Exception e) {
                log("九法降妖<酷狗> 推送开奖异常 " + url + " => " + e.getMessage());
            }
        }
    }

    /**
     * openTime = now + 45s：本函数在开奖后(≈官方drawTime)调用，此刻玩家下一局要到10s后才开始投注，
     * 其官方封盘时刻 = 下一局起点+35s ≈ now+45s。游戏服到 openTime 封盘→+5s开奖→+10s开奖动画，
     * 与官方 投注35/等待5/开奖10 完全对齐(修复"我方倒计时0时官方还剩10s"的早10s错位)。
     */
    private void pushGameTime() {
        long opentime = System.currentTimeMillis() + 45 * 1000;
        for (String url : DomainNameUtil.transitUrls) {
            try {
                String full = url + "/gameProxy/proxy/setGameTime?gameId=" + GAME_ID + "&time=" + opentime;
                String resp = httpGet(full);
                log("九法降妖<酷狗> 发送游戏时间 " + full + " => " + resp);
            } catch (Exception e) {
                log("九法降妖<酷狗> 发送游戏时间异常 " + e.getMessage());
            }
        }
    }

    // ============================================================
    // 酷狗 MainPage / RoundAward
    // ============================================================

    private MainPageResult requestMainPage() {
        String payload = "{\"activityId\":" + ACTIVITY_ID + ",\"roomId\":\"" + ROOM_ID + "\"}";
        try {
            PostResult post = postJson(MAIN_PAGE_URL, payload);
            if (isAuthFailed(post)) {
                log("九法降妖<酷狗> MainPage token 疑似失效: " + post.rawText);
                return new MainPageResult(null, "AUTH_FAILED");
            }
            if (post.statusCode != 200) {
                log("九法降妖<酷狗> MainPage HTTP 非200: " + post.statusCode + " " + post.rawText);
                return new MainPageResult(null, "HTTP_ERROR");
            }
            if (!(post.data instanceof Map)) {
                log("九法降妖<酷狗> MainPage 返回非JSON对象: " + post.rawText);
                return new MainPageResult(null, "PARSE_ERROR");
            }
            Map<?, ?> data = (Map<?, ?>) post.data;
            long serverTime = getRequiredLong(data, "serverTime");
            long roundId = getRequiredLong(data, "roundId");
            long localMid = (post.startMs + post.endMs) / 2L;
            long serverOffsetMs = serverTime - localMid;
            MainInfo info = new MainInfo(roundId, serverOffsetMs);
            log("九法降妖<酷狗> MainPage校准成功 serverTime=" + tsToStr(serverTime) + " roundId=" + roundId + " offset=" + serverOffsetMs + "ms");
            return new MainPageResult(info, "OK");
        } catch (Exception e) {
            log("九法降妖<酷狗> MainPage异常: " + e.getMessage());
            return new MainPageResult(null, "ERROR");
        }
    }

    private MainInfo safeRequestMainPageUntilOk() {
        while (!stop) {
            MainPageResult r = requestMainPage();
            if ("OK".equals(r.status) && r.info != null) {
                return r.info;
            }
            if ("AUTH_FAILED".equals(r.status)) {
                return waitAuthRecover();
            }
            sleepQuietly(5000L);
        }
        return null;
    }

    private MainInfo waitAuthRecover() {
        log("九法降妖<酷狗> token 疑似失效，进入等待恢复模式，请更新 token 后重启服务");
        while (!stop) {
            sleepQuietly(AUTH_RECHECK_SLEEP_MS);
            MainPageResult r = requestMainPage();
            if ("OK".equals(r.status) && r.info != null) {
                log("九法降妖<酷狗> token 已恢复");
                return r.info;
            }
        }
        return null;
    }

    private PostResult requestRoundAward(long roundId) throws Exception {
        String payload = "{\"activityId\":" + ACTIVITY_ID + ",\"roomId\":\"" + ROOM_ID + "\",\"roundId\":\"" + roundId + "\"}";
        return postJson(ROUND_AWARD_URL, payload);
    }

    private Map<?, ?> extractAward(Object dataObj) {
        if (!(dataObj instanceof Map)) {
            return null;
        }
        Map<?, ?> data = (Map<?, ?>) dataObj;
        if (data.get("beastInfo") instanceof Map) {
            return (Map<?, ?>) data.get("beastInfo");
        }
        if (data.get("data") instanceof Map) {
            Map<?, ?> inner = (Map<?, ?>) data.get("data");
            if (inner.get("beastInfo") instanceof Map) {
                return (Map<?, ?>) inner.get("beastInfo");
            }
            if (inner.get("award") instanceof Map) {
                Map<?, ?> award = (Map<?, ?>) inner.get("award");
                return award.get("beastInfo") instanceof Map ? (Map<?, ?>) award.get("beastInfo") : award;
            }
            if (inner.get("result") instanceof Map) {
                Map<?, ?> result = (Map<?, ?>) inner.get("result");
                return result.get("beastInfo") instanceof Map ? (Map<?, ?>) result.get("beastInfo") : result;
            }
        }
        if (data.get("result") instanceof Map) {
            Map<?, ?> result = (Map<?, ?>) data.get("result");
            return result.get("beastInfo") instanceof Map ? (Map<?, ?>) result.get("beastInfo") : result;
        }
        return null;
    }

    // ============================================================
    // HTTP（HttpURLConnection，与 KougouShenhai 一致）
    // ============================================================

    private PostResult postJson(String url, String payload) throws Exception {
        long startMs = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            for (Map.Entry<String, String> e : buildHeaders().entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
            byte[] body = payload.getBytes(UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int statusCode = conn.getResponseCode();
            InputStream is = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
            String rawText = readAll(is);
            long endMs = System.currentTimeMillis();

            Object data;
            try {
                data = parseJson(rawText);
            } catch (Exception e) {
                Map<String, Object> raw = new HashMap<String, Object>();
                raw.put("rawText", rawText);
                data = raw;
            }
            return new PostResult(statusCode, conn.getHeaderFields(), data, rawText, startMs, endMs);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String httpPostJson(String url, String json) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("content-type", "application/json; charset=utf-8");
            byte[] body = json.getBytes(UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            return readAll(is);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            return readAll(is);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> h = new HashMap<String, String>();
        h.put("accept", "application/json");
        h.put("content-type", "application/json");
        h.put("origin", "https://game.tme.kugou.com");
        h.put("referer", "https://game.tme.kugou.com/");
        h.put("user-agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36");
        h.put("x-auth-app-id", AUTH_APP_ID);
        h.put("x-auth-ticket", FALLBACK_TICKET);
        h.put("x-auth-token-type", AUTH_TOKEN_TYPE);
        h.put("x-auth-uid", AUTH_UID);
        return h;
    }

    private boolean isAuthFailed(PostResult result) {
        if (result == null) {
            return false;
        }
        if (result.statusCode == 401 || result.statusCode == 403) {
            return true;
        }
        String headerCode = firstHeader(result.headers, "X-Error-Code");
        if ("401".equals(headerCode) || "403".equals(headerCode) || "-10".equals(headerCode)
                || "10001".equals(headerCode) || "10002".equals(headerCode)) {
            return true;
        }
        if (result.data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result.data;
            Long code = toLongOrNull(map.get("code"));
            if (code != null && (code == -10L || code == 401L || code == 403L || code == 10001L || code == 10002L)) {
                return true;
            }
            String msg = String.valueOf(firstNonNull(map.get("msg"), map.get("message"), map.get("errorMsg"), map.get("errMsg"), ""));
            String lower = msg.toLowerCase(Locale.ROOT);
            if (msg.contains("登录") || msg.contains("鉴权") || lower.contains("token") || lower.contains("ticket")) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldForceSync(long nextRoundId, long serverOffsetMs) {
        long serverNow = System.currentTimeMillis() + serverOffsetMs;
        return serverNow > (nextRoundId + ROUND_DRAW_OFFSET_MS) + MAX_BEHIND_MS;
    }

    private void waitUntilServerTime(long targetServerMs, long serverOffsetMs) {
        while (!stop) {
            long remain = targetServerMs - (System.currentTimeMillis() + serverOffsetMs);
            if (remain <= 0) {
                return;
            }
            if (remain > 3000) {
                sleepQuietly(1000L);
            } else if (remain > 300) {
                sleepQuietly(remain / 2L);
            } else {
                sleepQuietly(remain);
            }
        }
    }

    // ============================================================
    // JSON(Nashorn) / 工具
    // ============================================================

    private Object parseJson(String json) throws ScriptException {
        if (json == null || json.trim().length() == 0) {
            return null;
        }
        String script = "Java.asJSONCompatible(JSON.parse(" + quoteForJs(json) + "))";
        return jsonEngine.eval(script);
    }

    private String quoteForJs(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 32 || c > 126) {
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
        sb.append('\'');
        return sb.toString();
    }

    private long getRequiredLong(Map<?, ?> map, String key) {
        Long v = toLongOrNull(map.get(key));
        if (v == null) {
            throw new IllegalArgumentException("缺少字段或非数字: " + key + ", value=" + map.get(key));
        }
        return v;
    }

    private Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toIntegerOrNull(Object value) {
        Long v = toLongOrNull(value);
        return v == null ? null : v.intValue();
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && name.equalsIgnoreCase(e.getKey())) {
                List<String> v = e.getValue();
                if (v != null && !v.isEmpty()) {
                    return v.get(0);
                }
            }
        }
        return null;
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        try {
            while ((len = in.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
        } finally {
            in.close();
        }
        return new String(baos.toByteArray(), UTF_8);
    }

    private String tsToStr(long ms) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(ms));
        } catch (Exception e) {
            return String.valueOf(ms);
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(Math.max(ms, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop = true;
        }
    }

    /** 用 System.out 直接打印，确保不受框架日志配置影响一定可见 */
    private void log(String msg) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [九法降妖] " + msg);
    }

    // ============================================================
    // 数据结构
    // ============================================================

    private static class PostResult {
        final int statusCode;
        final Map<String, List<String>> headers;
        final Object data;
        final String rawText;
        final long startMs;
        final long endMs;

        PostResult(int statusCode, Map<String, List<String>> headers, Object data, String rawText, long startMs, long endMs) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.data = data;
            this.rawText = rawText;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static class MainInfo {
        final long roundId;
        final long serverOffsetMs;

        MainInfo(long roundId, long serverOffsetMs) {
            this.roundId = roundId;
            this.serverOffsetMs = serverOffsetMs;
        }
    }

    private static class MainPageResult {
        final MainInfo info;
        final String status;

        MainPageResult(MainInfo info, String status) {
            this.info = info;
            this.status = status;
        }
    }
}
