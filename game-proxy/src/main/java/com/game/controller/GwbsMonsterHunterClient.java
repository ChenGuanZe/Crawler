package com.game.controller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class GwbsMonsterHunterClient {

    // ===================== 基础配置（对齐 Python） =====================
    private static final String LOGIN_NAME = "+8615641762326";
    private static final String LOGIN_PW   = "Zaizai0308";

    private static final String ROOM_ID  = "8_888888";
    private static final String CMD_NAME = "onmonsterhunter_currstatus";
    private static final int PLATFORM_ID = 5;

    private static final String PROXY_HOST = "http://8.218.223.24:8085";
    private static final String SET_GAME_TIME_URL = PROXY_HOST + "/gameProxy/proxy/setGameTime";
    private static final String DEL_GAME_TIME_URL = PROXY_HOST + "/gameProxy/proxy/delGameTime";
    private static final int GAME_ID = 20;

    private static final String UPLOAD_URL = "http://134.122.128.242:8081/wanshunGame/bjxgwbs/luckyMonster";

    private static final String LOGIN_URL = "https://auth.suxinwlkj.com/api/xauth/login";
    private static final String USERINFO_URL = "https://api-live.suxinwlkj.com/milive.bizprofile.s/v1/get_member_own_info";
    private static final String TICKER_URL = "https://api-live.suxinwlkj.com/xllivemp.basemsglogic.s/v1/Ticker.json";
    private static final String WS_BASE = "wss://game-msg.suxinwlkj.com/ws?ticker=";

    // ===================== 客户端/JSON =====================
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(15))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ===================== 运行态状态（对齐 Python） =====================
    private volatile boolean gameTimeSet = false;
    private volatile String lastSentRoundFp = null;

    private final AtomicLong messageId = new AtomicLong(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // ===================== Entry =====================
    public static void main(String[] args) {
        GwbsMonsterHunterClient client = new GwbsMonsterHunterClient();
        while (true) {
            try {
                client.runOnce(); // 断线会抛异常，外层重连
            } catch (Exception e) {
                System.out.println("WS closed by server, wait 3s... " + e);
                e.printStackTrace();
                sleepSilently(3000);
            }
        }
    }

    private void runOnce() throws Exception {
        AuthInfo auth = getAccessTokenAndUid();
        String ticker = getTicker(auth.uid, auth.bearerToken);
        String wsUrl = WS_BASE + ticker;

        System.out.println("Connect: " + wsUrl);

        Request req = new Request.Builder().url(wsUrl).build();

        // 用 latch 让 runOnce 阻塞直到 WS 断开
        CountDownLatch closeLatch = new CountDownLatch(1);

        WebSocketListener listener = new WebSocketListener() {
            private ScheduledFuture<?> pingFuture;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("WS connected");

                // 1) 进房（对齐 Python：topicName=default, messageId=0, payload=string）
                Map<String, Object> inroomPayload = new LinkedHashMap<>();
                inroomPayload.put("cmd", "inroom");
                inroomPayload.put("platform", "pc");
                inroomPayload.put("userId", auth.uid);
                inroomPayload.put("roomId", ROOM_ID);
                inroomPayload.put("platformId", PLATFORM_ID);
                inroomPayload.put("ext", "");

                Map<String, Object> first = new LinkedHashMap<>();
                first.put("topicName", "default");
                first.put("messageId", 0);
                first.put("payload", toJsonQuiet(inroomPayload)); // payload 是 JSON 字符串

                webSocket.send(toJsonQuiet(first));

                // 2) ping loop：每 3 秒发一次 {"topicName":"ping","messageId":n,"payload":""}
                pingFuture = scheduler.scheduleAtFixedRate(() -> {
                    long id = messageId.incrementAndGet();
                    Map<String, Object> ping = new LinkedHashMap<>();
                    ping.put("topicName", "ping");
                    ping.put("messageId", id);
                    ping.put("payload", "");
                    try {
                        webSocket.send(MAPPER.writeValueAsString(ping));
                    } catch (Exception ignore) {
                        // ignore
                    }
                }, 3, 3, TimeUnit.SECONDS);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    handleWsMessage(text);
                } catch (Exception e) {
                    // 不要因为单条消息解析问题导致断线
                    System.out.println("[onMessage error] " + e);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("WS closing: " + code + " " + reason);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("WS closed: " + code + " " + reason);
                if (pingFuture != null) pingFuture.cancel(true);
                closeLatch.countDown();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.out.println("WS failure: " + t);
                if (pingFuture != null) pingFuture.cancel(true);
                closeLatch.countDown();
            }
        };

        // 打开 WS
        WebSocket ws = HTTP.newWebSocket(req, listener);

        // 等断开（被 server close 是正常现象，对齐 Python 行为）
        closeLatch.await();

        // 主动释放
        ws.cancel();
    }

    // ===================== WS 消息处理（对齐 Python 逻辑） =====================
    private void handleWsMessage(String raw) throws Exception {
        Map<String, Object> outer = tryParseObject(raw);
        if (outer == null) {
            System.out.println("[SKIP RAW outer] " + raw);
            return;
        }

        Object payloadField = outer.get("payload");
        Map<String, Object> payloadObj = tryParseObject(payloadField);
        if (payloadObj == null) {
            return;
        }

        Object cmd = payloadObj.get("cmd");
        if (!CMD_NAME.equals(String.valueOf(cmd))) return;

        Map<String, Object> statusData = asMap(payloadObj.get("status_data"));
        Object statusObj = statusData != null ? statusData.get("status") : null;

        Integer status = toInt(statusObj);
        Object countdown = statusData != null ? statusData.get("countdown") : null;
        Object name = statusData != null ? statusData.get("name") : null;

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("status", status);
        log.put("countdown", countdown);
        log.put("name", name);
        System.out.println(log);

        if (status == null) return;

        // status == 0：准备中 -> setGameTime（仅一次），并允许新局再发
        if (status == 0) {
            lastSentRoundFp = null;
            if (!gameTimeSet) {
                setGameTime();
                gameTimeSet = true;
            }
            return;
        }

        // status == 2：开奖 -> 必须有 other_data.open_position 才上传
        if (status == 2) {
            Map<String, Object> other = asMap(payloadObj.get("other_data"));
            Map<String, Object> openPos = null;
            if (other != null) openPos = asMap(other.get("open_position"));

            if (openPos == null || openPos.isEmpty()) {
                System.out.println("[GW] status=2 but other_data/open_position missing, skip");
                return;
            }

            String fp = makeRoundFingerprint(payloadObj);
            if (fp.equals(lastSentRoundFp)) {
                return; // 本局只发一次
            }

            System.out.println("[GW RESULT] " + openPos);

            // Python: status==2 时 delGameTime
            delGameTime();

            Map<String, Object> body = buildUploadBody(outer, payloadObj);

            // debug（对齐 Python）
            Object result = body.get("result");
            Object payload = (result instanceof Map) ? ((Map<?, ?>) result).get("payload") : null;
            System.out.println("[DEBUG] upload result.payload type: " + (payload == null ? "null" : payload.getClass()));
            Object gameInfo = body.get("gameInfo");
            if (gameInfo instanceof Map) {
                System.out.println("[DEBUG] other_data keys: " + ((Map<?, ?>) gameInfo).keySet());
            }

            uploadResult(body);
            lastSentRoundFp = fp;
            return;
        }

        // status == 3：动画/奔袭 -> 允许下局 status==0 再 set
        if (status == 3) {
            gameTimeSet = false;
        }
    }

    // ===================== HTTP：登录/用户信息 =====================
    private static AuthInfo getAccessTokenAndUid() throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "qqxlive-mobile");
        body.put("signinMethod", "Password");
        body.put("username", LOGIN_NAME);
        body.put("password", LOGIN_PW);
        body.put("countryCode", "+86");

        String json = MAPPER.writeValueAsString(body);

        Request req = new Request.Builder()
                .url(LOGIN_URL)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            String respText = bodyString(resp);
            if (!resp.isSuccessful()) throw new IOException("login failed: " + resp.code() + " " + respText);

            Map<String, Object> root = parseJsonObject(respText);
            Object dataObj = root.get("data");
            if (!(dataObj instanceof Map)) throw new IOException("login parse error: data missing");

            String accessToken = String.valueOf(((Map<?, ?>) dataObj).get("accessToken"));
            String bearer = "Bearer " + accessToken;

            // 取 uid
            Map<String, Object> ubody = new LinkedHashMap<>();
            ubody.put("login_type", "0");

            Request req2 = new Request.Builder()
                    .url(USERINFO_URL)
                    .addHeader("Authorization", bearer)
                    .post(RequestBody.create(MAPPER.writeValueAsString(ubody), MediaType.parse("application/json")))
                    .build();

            try (Response resp2 = HTTP.newCall(req2).execute()) {
                String resp2Text = bodyString(resp2);
                if (!resp2.isSuccessful()) throw new IOException("userinfo failed: " + resp2.code() + " " + resp2Text);

                Map<String, Object> root2 = parseJsonObject(resp2Text);
                Object data2Obj = root2.get("data");
                if (!(data2Obj instanceof Map)) throw new IOException("userinfo parse error: data missing");

                Map<?, ?> data = (Map<?, ?>) data2Obj;
                Object personalInfoObj = data.get("personalInfo");
                if (!(personalInfoObj instanceof Map)) throw new IOException("userinfo parse error: personalInfo missing");

                Map<?, ?> personalInfo = (Map<?, ?>) personalInfoObj;
                String uid = String.valueOf(personalInfo.get("zuid"));
                return new AuthInfo(uid, bearer);
            }
        }
    }

    private static String getTicker(String uid, String bearerToken) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connType", "websocket");
        body.put("id", uid);
        body.put("actType", 0);
        body.put("devId", "java-gwbs");
        body.put("checkUuid", uid);

        Request req = new Request.Builder()
                .url(TICKER_URL)
                .addHeader("Authorization", bearerToken)
                .post(RequestBody.create(MAPPER.writeValueAsString(body), MediaType.parse("application/json")))
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            String respText = bodyString(resp);
            if (!resp.isSuccessful()) throw new IOException("ticker failed: " + resp.code() + " " + respText);

            Map<String, Object> root = parseJsonObject(respText);
            Object dataObj = root.get("data");
            if (!(dataObj instanceof Map)) throw new IOException("ticker parse error: data missing");

            String tk = String.valueOf(((Map<?, ?>) dataObj).get("token"));
            System.out.println("Ticker OK: " + tk);
            return tk;
        }
    }

    // ===================== gameProxy：set/del（对齐 Python） =====================
    private static void setGameTime() {
        // Python: ts = int((time.time() + 40) * 1000)
        long ts = (Instant.now().getEpochSecond() + 40) * 1000L;
        HttpUrl url = HttpUrl.parse(SET_GAME_TIME_URL).newBuilder()
                .addQueryParameter("gameId", String.valueOf(GAME_ID))
                .addQueryParameter("time", String.valueOf(ts))
                .build();

        System.out.println("完整请求" + url);
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String respText = bodyString(resp);
            System.out.println("[GW setGameTime] " + resp.code() + " " + respText);
        } catch (Exception e) {
            System.out.println("[GW setGameTime error] " + e);
        }
    }

    private static void delGameTime() {
        HttpUrl url = HttpUrl.parse(DEL_GAME_TIME_URL).newBuilder()
                .addQueryParameter("gameId", String.valueOf(GAME_ID))
                .build();

        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String respText = bodyString(resp);
            System.out.println("[GW delGameTime] " + resp.code() + " " + respText);
        } catch (Exception e) {
            System.out.println("[GW delGameTime error] " + e);
        }
    }

    // ===================== 上传 payload（对齐 Python build_upload_body） =====================
    private static Map<String, Object> buildUploadBody(Map<String, Object> outerObj, Map<String, Object> payloadObj) {
        Map<String, Object> other = asMap(payloadObj.get("other_data"));
        if (other == null) other = new LinkedHashMap<>();

        // result = outer + payload(对象，不是字符串)
        Map<String, Object> resultObj = new LinkedHashMap<>(outerObj);
        resultObj.put("payload", payloadObj);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameInfo", other);
        body.put("result", resultObj);
        body.put("code", 1);
        return body;
    }

    private static void uploadResult(Map<String, Object> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            Request req = new Request.Builder()
                    .url(UPLOAD_URL)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response resp = HTTP.newCall(req).execute()) {
                String respText = bodyString(resp);
                System.out.println("[GW UPLOAD] " + resp.code() + " " + respText);
            }
        } catch (Exception e) {
            System.out.println("[GW UPLOAD error] " + e);
        }
    }

    // ===================== round 指纹（对齐 Python make_round_fingerprint） =====================
    private static String makeRoundFingerprint(Map<String, Object> payloadObj) {
        Map<String, Object> other = asMap(payloadObj.get("other_data"));
        if (other == null) other = Collections.emptyMap();

        // 排序稳定化：TreeMap
        Map<String, Object> sorted = new TreeMap<>(other);
        try {
            return MAPPER.writeValueAsString(sorted);
        } catch (Exception e) {
            return String.valueOf(sorted);
        }
    }

    // ===================== 工具方法 =====================
    private static Map<String, Object> tryParseObject(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) raw;
            return m;
        }
        if (!(raw instanceof String)) return null;

        String s = (String) raw;
        try {
            return MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Map<String, Object> parseJsonObject(String s) throws JsonProcessingException {
        return MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
    }

    private static String toJsonQuiet(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) obj;
            return m;
        }
        return null;
    }

    private static Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        if (obj instanceof Double) return ((Double) obj).intValue();
        if (obj instanceof String) {
            try { return Integer.parseInt((String) obj); } catch (Exception ignore) {}
        }
        return null;
    }

    private static void sleepSilently(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // 读取 ResponseBody（OkHttp body 只能读一次，所以都先读到变量再用）
    private static String bodyString(Response resp) throws IOException {
        if (resp == null) return "";
        ResponseBody b = resp.body();
        return b == null ? "" : b.string();
    }

    private static class AuthInfo {
        final String uid;
        final String bearerToken;
        AuthInfo(String uid, String bearerToken) {
            this.uid = uid;
            this.bearerToken = bearerToken;
        }
    }
}
