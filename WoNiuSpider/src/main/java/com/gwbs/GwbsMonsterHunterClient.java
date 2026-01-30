package com.gwbs;

import com.commom.RestTemplateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@ClientEndpoint
public class GwbsMonsterHunterClient {

    public RestTemplateUtils restTemplateUtils;

    private static final Logger logger = LoggerFactory.getLogger(GwbsMonsterHunterClient.class);
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

    private static long lastOpenTime=0;

    public GwbsMonsterHunterClient(RestTemplateUtils restTemplateUtils){
        this.restTemplateUtils=restTemplateUtils;


    }

    // ===================== 客户端/JSON =====================
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(15))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ===================== 运行态状态（对齐 Python） =====================
    private volatile boolean gameTimeSet = false;
    private volatile String lastSentRoundFp = null;

    private final AtomicLong messageId = new AtomicLong(0);
//    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private Session session;
    private AuthInfo auth;

    @OnMessage
    public void pongMessage(Session session, PongMessage msg) {
        logger.info("[<GBWS>]收到的PongMessage消息: {}", msg);
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("[<Gw>]onOpen");
        lastOpenTime=System.currentTimeMillis(); //记录最后一次连接时间
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
        first.put("payload", DataUtils.toJsonQuiet(inroomPayload)); // payload 是 JSON 字符串

        try {
            session.getBasicRemote().sendText(DataUtils.toJsonQuiet(first));
            logger.info("[<Gw>] 发送进入房间数据 {}",DataUtils.toJsonQuiet(first));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @OnClose
    public void onClose() {
        logger.error("[<Gw>]链接关闭!Close to server");
    }

    @OnError
    public void onError(Throwable e, Session session) {
        logger.error("[<Gw>]监听到异常", e);
    }

    @OnMessage
    public void binaryMessage(Session session, ByteBuffer msg) {
//        logger.info("[gw]收到的ByteBuffer消息: {}", );
        String message =StandardCharsets.UTF_8.decode(msg).toString();
        logger.info("[gw]收到的ByteBuffer消息: {}",message);

        Map<String, Object> outer = DataUtils.tryParseObject(message);
        if (outer == null) {
            logger.info("[SKIP RAW outer] ");
            return;
        }
        Object payloadField = outer.get("payload");
        Map<String, Object> payloadObj = DataUtils.tryParseObject(payloadField);
        if (payloadObj == null) {
            logger.info("[payloadObj] is null " );
            return;
        }

        Object cmd = payloadObj.get("cmd");
        if (!CMD_NAME.equals(String.valueOf(cmd))) return;

        Map<String, Object> statusData = DataUtils.asMap(payloadObj.get("status_data"));
        Object statusObj = statusData != null ? statusData.get("status") : null;

        Integer status = DataUtils.toInt(statusObj);
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
            if(System.currentTimeMillis()-lastOpenTime>1000*60*60) {
                logger.info("最后连接时间大于:{},主动断开重连","1小时");
                try {
                    session.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return;
        }

        // status == 2：开奖 -> 必须有 other_data.open_position 才上传
        if (status == 2) {
            Map<String, Object> other = DataUtils.asMap(payloadObj.get("other_data"));
            Map<String, Object> openPos = null;
            if (other != null) openPos = DataUtils.asMap(other.get("open_position"));

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

            sendGameServerResult(body);
            lastSentRoundFp = fp;
            return;
        }

        // status == 3：动画/奔袭 -> 允许下局 status==0 再 set
        if (status == 3) {
            gameTimeSet = false;
        }

    }
    public static String decodeBytes(byte[] raw) {
        if (raw == null) {
            return null;
        }
        return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        logger.info("[Gw]收到的String消息: {}", message);

    }

    private static void sendGameServerResult(Map<String, Object> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            logger.info("[GW] send server data is :{}",json);
            Request req = new Request.Builder()
                    .url(UPLOAD_URL)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response resp = HTTP.newCall(req).execute()) {
                String respText = bodyString(resp);
                logger.error("[GW UPLOAD] :{}" + resp.code() + " " + respText);
            }
        } catch (Exception e) {
            logger.error("[GW UPLOAD error] :{}" + e);
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

            Map<String, Object> root = DataUtils.parseJsonObject(respText);
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

                Map<String, Object> root2 = DataUtils.parseJsonObject(resp2Text);
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

            Map<String, Object> root = DataUtils.parseJsonObject(respText);
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
        Map<String, Object> other = DataUtils.asMap(payloadObj.get("other_data"));
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



    // ===================== round 指纹（对齐 Python make_round_fingerprint） =====================
    private static String makeRoundFingerprint(Map<String, Object> payloadObj) {
        Map<String, Object> other = DataUtils.asMap(payloadObj.get("other_data"));
        if (other == null) other = Collections.emptyMap();

        // 排序稳定化：TreeMap
        Map<String, Object> sorted = new TreeMap<>(other);
        try {
            return MAPPER.writeValueAsString(sorted);
        } catch (Exception e) {
            return String.valueOf(sorted);
        }
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

    private synchronized void connect() {
        try {
            logger.info("<GWBS>准备链接ws服务...");
            WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
            webSocketContainer.setDefaultMaxTextMessageBufferSize(65536);
            webSocketContainer.setDefaultMaxBinaryMessageBufferSize(65536);
            webSocketContainer.setDefaultMaxSessionIdleTimeout(30000); // 10秒
            webSocketContainer.setAsyncSendTimeout(20000);

            auth = getAccessTokenAndUid();
            String ticker = getTicker(auth.uid, auth.bearerToken);
            String wsUrl = WS_BASE + ticker;

            if (wsUrl == null || "".equals(wsUrl)) {
                logger.info("<GWBS>远程获取游戏url[{}] ", wsUrl);
                return;
            }

            String wsUrlStr = wsUrl;
            logger.info("<GWBS>准备链接ws服务 => {}", wsUrlStr);
            URI uri = new URI(wsUrlStr);
            session = webSocketContainer.connectToServer(this, uri);
        } catch (Exception e) {
            logger.error("<GWBS>[{}]链接失败", e);
        }
    }

    public synchronized void report() {
        if (session == null || !session.isOpen()) {
            logger.info("[<GWBS>]已关闭，进行重新链接");
            connect();
        }
        try {
            long id = messageId.incrementAndGet();
            Map<String, Object> ping = new LinkedHashMap<>();
            ping.put("topicName", "ping");
            ping.put("messageId", id);
            ping.put("payload", "");
            try {
                session.getBasicRemote().sendText(DataUtils.toJsonQuiet(ping));
                logger.info("[reportGameGw_WS] 上报 - 开始");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }catch (Exception e){
            logger.error("<Gw>[{}]心跳异常 {}", e);
        }

    }


    public static void main(String[] args) {
        RestTemplateUtils restTemplateUtils=new RestTemplateUtils();
        GwbsMonsterHunterClient client = new GwbsMonsterHunterClient(restTemplateUtils);
        logger.debug("[<GWBS>]启动");
        while (true) {
            try {

                client.report();
                Thread.sleep(1000 * 3);
                //log.info("[reportGame1015WS] 上报 - 结束");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

