package com.listener;

import com.utils.DomainNameUtil;
import com.utils.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 探岛寻宝 WebSocket 采集端（新数据源）。
 * <p>
 * 与旧的 {@link TdxbGamePoller}（来疯 mtop 秒级轮询）相比，本数据源是服务端主动推送，
 * 自带期号(sn/id)和官方开奖时间(open_time)，但**结果比官方实际开奖晚十几秒才到**。
 * 这个延迟会破坏游戏侧原有的时间轴（投注截止时官方已开奖 + 到点取不到结果判漏单），
 * 因此上线前必须先量出真实延迟、期号步长、官方周期三个数，再据此调整 wanshunGame 的阶段时长。
 * <p>
 * 所以本类默认运行在**观测模式**：只连接、只解析、只打日志，不向 wanshunGame 推任何数据，
 * 与线上仍在跑的 {@link TdxbGamePoller} 完全不冲突。观测数据够了之后再用
 * {@code -Dtdxb.ws.push=true} 切到推送模式接管开奖。
 * <p>
 * 可调参数（JVM 启动参数，改完不用重新编译）：
 * <pre>
 *   -Dtdxb.ws.host=8.212.0.177     数据源地址
 *   -Dtdxb.ws.connections=2        冗余连接数（错峰，任一条断线其余继续收）
 *   -Dtdxb.ws.push=false           是否真正推送给 wanshunGame（观测期保持 false）
 *   -Dtdxb.ws.period=60            官方开奖周期(秒)，观测日志会验证这个值
 *   -Dtdxb.ws.betLead=5            游戏投注截止提前于官方开奖的秒数（安全边界，防投注穿越）
 *   -Dtdxb.ws.raw=true             是否打印原始报文
 * </pre>
 */
@Slf4j
public class TdxbWsPoller {

    private static final String GAME_NAME = "探岛寻宝WS";
    private static final int GAME_ID = 29;
    /** 数据源里探岛寻宝的游戏类型编号 */
    private static final int SOURCE_GAME_TYPE = 21;
    private static final String LOG_TAG = "[探岛WS]";

    private static final String[] ISLANDS = {
            "未知岛屿", "龙鳞岛", "蓝海岛", "紫烟岛", "银月岛",
            "梦境岛", "绿洲岛", "黑石岛", "凤舞岛"
    };

    private static final Pattern KAIJIANG_PATTERN =
            Pattern.compile("\"type\"\\s*:\\s*\"kaijiang\"", Pattern.CASE_INSENSITIVE);

    private final String host = System.getProperty("tdxb.ws.host", "8.212.0.177");
    private final int connections = intProp("tdxb.ws.connections", 2, 1, 8);
    private final boolean pushEnabled = Boolean.parseBoolean(System.getProperty("tdxb.ws.push", "false"));
    private final long periodMs = intProp("tdxb.ws.period", 60, 5, 600) * 1000L;
    /** 爬虫的 open_time 相对来疯官方实际开奖的滞后秒数；决定投注要提前多久截止，问不到就保守取大 */
    private final long sourceLagMs = intProp("tdxb.ws.sourceLag", 20, 0, 45) * 1000L;
    /** 投注截止再提前于官方开奖的安全边界 */
    private final long betLeadMs = intProp("tdxb.ws.betLead", 5, 0, 30) * 1000L;
    /** 游戏开奖时刻留在 open_time 之后的余量；实测到达延迟 1~2s，4s 是三倍冗余 */
    private static final long RESULT_MARGIN_MS = 4000L;
    private final boolean printRaw = Boolean.parseBoolean(System.getProperty("tdxb.ws.raw", "true"));

    private final long staggerMillis = 20000L;
    private final long reconnectDelayMillis = 3000L;

    private final BlockingQueue<Event> events = new ArrayBlockingQueue<Event>(10000);
    private final ConcurrentHashMap<Integer, Thread> workers = new ConcurrentHashMap<Integer, Thread>();
    private final ConcurrentHashMap<Integer, Boolean> connected = new ConcurrentHashMap<Integer, Boolean>();

    /** 已处理过的期号，用于多线路收到同一期时去重 */
    private final LinkedHashSet<String> handled = new LinkedHashSet<String>();

    private String lastPeriod;
    private long lastOfficialMs;
    private long lastReceivedMs;
    private int otherMessageLogged;

    /** 可脱离 game-yk 服务单独跑观测，方便在不动线上的前提下抓一段日志分析延迟 */
    public static void main(String[] args) throws Exception {
        new TdxbWsPoller().start();
    }

    /** 阻塞运行，由 YkService 放在守护线程里循环调用；内部自带重连，正常不会返回 */
    public void start() throws Exception {
        URI uri = buildUri();
        log.info("{} 启动：数据源={}，冗余连接={}，模式={}，官方周期={}s，爬虫滞后={}s，投注提前={}s"
                        + " ==> wanshunGame 侧 base_configvalue 里的 TdxbCloseSecond 必须配成 {}",
                LOG_TAG, uri, connections,
                pushEnabled ? "推送(会接管开奖)" : "观测(只打日志、不推送)",
                periodMs / 1000, sourceLagMs / 1000, betLeadMs / 1000, recommendedCloseSecond());
        if (!pushEnabled) {
            log.info("{} 观测模式说明：本采集端不会向 wanshunGame 推送任何数据，线上开奖仍由旧的 mtop 轮询"
                    + "(TdxbGamePoller)负责；确认延迟数据后再加 -Dtdxb.ws.push=true 切换。", LOG_TAG);
        }

        for (int id = 1; id <= connections; id++) {
            startWorker(id, uri, (id - 1L) * staggerMillis);
        }

        long lastHealthReport = System.currentTimeMillis();
        while (true) {
            for (int id = 1; id <= connections; id++) {
                Thread thread = workers.get(id);
                if (thread == null || !thread.isAlive()) {
                    connected.put(id, Boolean.FALSE);
                    log.warn("{} 线路{} 工作线程已退出，正在自动重建……", LOG_TAG, id);
                    startWorker(id, uri, 5000L);
                }
            }

            long now = System.currentTimeMillis();
            if (now - lastHealthReport >= 300000L) {
                int healthy = 0;
                for (int id = 1; id <= connections; id++) {
                    if (Boolean.TRUE.equals(connected.get(id))) {
                        healthy++;
                    }
                }
                long silent = lastReceivedMs == 0 ? -1 : (now - lastReceivedMs) / 1000;
                log.info("{} 健康检查：{}/{} 条线路已连接，最近一期距今 {}s", LOG_TAG, healthy, connections, silent);
                lastHealthReport = now;
            }

            Event event = events.poll(1L, TimeUnit.SECONDS);
            if (event == null) {
                continue;
            }
            try {
                handleEvent(event);
            } catch (Throwable error) {
                log.error("{} 单条消息处理异常，已忽略", LOG_TAG, error);
            }
        }
    }

    private void handleEvent(Event event) {
        if (event.type == EventType.STATUS) {
            connected.put(event.workerId, event.connected);
            log.info("{} 线路{} {}", LOG_TAG, event.workerId, event.text);
            return;
        }
        if (event.type == EventType.ERROR) {
            connected.put(event.workerId, Boolean.FALSE);
            log.warn("{} 线路{} 连接异常：{}；将自动重连……", LOG_TAG, event.workerId, event.text);
            return;
        }

        if (!KAIJIANG_PATTERN.matcher(event.text).find()) {
            // 非开奖报文(心跳/其它游戏)：只在刚连上时打几条用来认协议，之后不再刷屏
            if (otherMessageLogged < 5) {
                otherMessageLogged++;
                log.info("{} 非开奖报文样本{}：{}", LOG_TAG, otherMessageLogged, brief(event.text));
            }
            return;
        }

        String gameType = field(event.text, "game_type");
        if (!String.valueOf(SOURCE_GAME_TYPE).equals(gameType)) {
            return;
        }

        Integer number = intField(event.text, "number");
        if (number == null) {
            log.warn("{} 开奖报文缺少 number 字段：{}", LOG_TAG, brief(event.text));
            return;
        }

        String period = firstNonEmpty(field(event.text, "sn"), field(event.text, "qishu"),
                field(event.text, "period"), field(event.text, "id"));
        String dedupeKey = period != null ? period : (number + "@" + field(event.text, "open_time"));
        if (!markHandled(dedupeKey)) {
            return;
        }

        long receivedMs = System.currentTimeMillis();
        String openTimeRaw = field(event.text, "open_time");
        long officialMs = parseEpochMillis(openTimeRaw);

        logDraw(event.workerId, number, period, openTimeRaw, officialMs, receivedMs, event.text);

        if (period != null) {
            int missing = missedPeriods(lastPeriod, period);
            if (missing > 0) {
                log.warn("{} 期号从 {} 跳到 {}，中间可能遗漏 {} 期", LOG_TAG, lastPeriod, period, missing);
            }
            if (lastPeriod == null || period.compareTo(lastPeriod) > 0) {
                lastPeriod = period;
            }
        }
        lastOfficialMs = officialMs;
        lastReceivedMs = receivedMs;

        if (pushEnabled) {
            pushLottery(number, period, officialMs);
            pushGameTime(officialMs, receivedMs);
        }
    }

    /** 一期一行的观测日志：延迟/间隔/双源对账全在这里，方便直接 grep 出来分析 */
    private void logDraw(int workerId, int number, String period, String openTimeRaw,
                         long officialMs, long receivedMs, String raw) {
        String island = number >= 1 && number <= 8 ? ISLANDS[number] : ISLANDS[0];

        StringBuilder sb = new StringBuilder(256);
        sb.append(LOG_TAG).append(" 开奖")
                .append(" 期号=").append(period == null ? "无" : period)
                .append(" 号码=").append(number).append('(').append(island).append(')')
                .append(" 收到=").append(time(receivedMs));

        if (officialMs > 0) {
            sb.append(" 官方开奖=").append(time(officialMs))
                    .append(" 延迟=").append(receivedMs - officialMs).append("ms");
        } else {
            sb.append(" 官方开奖=解析失败(open_time原文=").append(openTimeRaw).append(')');
        }
        if (lastReceivedMs > 0) {
            sb.append(" 距上期收到=").append(receivedMs - lastReceivedMs).append("ms");
        }
        if (officialMs > 0 && lastOfficialMs > 0) {
            long gap = officialMs - lastOfficialMs;
            sb.append(" 官方间隔=").append(gap).append("ms");
            if (Math.abs(gap - periodMs) > 3000L) {
                sb.append("(与配置的 ").append(periodMs / 1000).append("s 周期不符，注意)");
            }
        }
        sb.append(" 线路=").append(workerId);
        log.info(sb.toString());

        // 双源对账：新源号码是否和旧的 mtop 轮询一致，切换前必须对得上
        Integer oldId = TdxbGamePoller.getLastIslandId();
        if (oldId != null) {
            long oldAge = System.currentTimeMillis() - TdxbGamePoller.getLastIslandTime();
            if (oldAge <= periodMs) {
                log.info("{} 双源对账 新源={} 旧源(mtop)={} {} (旧源数据 {}ms 前)",
                        LOG_TAG, number, oldId, oldId.intValue() == number ? "一致" : "★不一致★", oldAge);
            }
        }

        if (printRaw) {
            log.info("{} 原始报文 期号={}：{}", LOG_TAG, period, brief(raw));
        }
    }

    /** 推送开奖号：新增 roundId/openTime 两个字段，让游戏服能按期号配对而不是靠时间窗猜 */
    private void pushLottery(int number, String period, long officialMs) {
        StringBuilder json = new StringBuilder(96);
        json.append("{\"monsterId\":").append(number).append(",\"code\":1");
        if (period != null && isDigits(period)) {
            json.append(",\"roundId\":").append(period);
        }
        if (officialMs > 0) {
            json.append(",\"openTime\":").append(officialMs);
        }
        json.append('}');

        for (String url : DomainNameUtil.urls) {
            String fullUrl = url + "/tdxb/luckyMonster";
            try {
                String resp = OkHttpUtil.postJson(fullUrl, json.toString());
                log.info("{} 推送开奖 {} => {} 响应：{}", LOG_TAG, json, fullUrl, resp);
            } catch (Exception e) {
                log.warn("{} 推送开奖异常 {}：{}", LOG_TAG, fullUrl, e.getMessage());
            }
        }
    }

    /**
     * 推送游戏时间。
     * <p>
     * 这里是与旧采集端最关键的差别：旧的是 {@code now + 60s}，而 now 已经比官方开奖晚了十几秒，
     * 会把游戏的投注窗口整体拖到官方开奖之后（玩家可以看着官方结果再下注）。
     * 新算法直接锚定官方开奖时间：{@code 下期官方开奖 - betLead}，让投注一定在官方开奖前截止。
     */
    private void pushGameTime(long officialMs, long receivedMs) {
        long anchor = officialMs > 0 ? officialMs : receivedMs - 2000L;
        // 锚定 open_time 而不是 now：now 已经晚于官方开奖十几秒，用 now+60 会把整个投注窗口
        // 平移到官方开奖之后，玩家能看着官方结果再回来下注。
        // 下期投注截止(封盘) = 下期官方实际开奖 - 安全边界 = (本期open_time + 周期 - 爬虫滞后) - 安全边界
        long openTime = anchor + periodMs - sourceLagMs - betLeadMs;
        if (openTime <= System.currentTimeMillis()) {
            log.warn("{} 推算出的投注截止时间 {} 已经过去了(锚点={}，周期={}s，滞后={}s，边界={}s)，本期跳过 setGameTime",
                    LOG_TAG, time(openTime), time(anchor), periodMs / 1000,
                    sourceLagMs / 1000, betLeadMs / 1000);
            return;
        }

        for (String url : DomainNameUtil.transitUrls) {
            String fullUrl = url + "/gameProxy/proxy/setGameTime?gameId=" + GAME_ID + "&time=" + openTime;
            try {
                String resp = OkHttpUtil.get(fullUrl, null);
                log.info("{} 推送投注截止时间 {}(={})，游戏应在 {} 开奖(TdxbCloseSecond={}s) => {} 响应：{}",
                        LOG_TAG, openTime, time(openTime),
                        time(anchor + periodMs + RESULT_MARGIN_MS), recommendedCloseSecond(), fullUrl, resp);
            } catch (Exception e) {
                log.warn("{} 推送投注截止时间异常 {}：{}", LOG_TAG, fullUrl, e.getMessage());
            }
        }
    }

    /**
     * 游戏侧应配的封盘时长(秒)：这段时间要同时盖住"投注提前截止的边界"和"等官方开奖+等结果送达"。
     * 采集端和游戏侧必须用同一套账，否则每期都会因为对不上而判漏单。
     */
    private long recommendedCloseSecond() {
        return (sourceLagMs + betLeadMs + RESULT_MARGIN_MS) / 1000L;
    }

    private boolean markHandled(String key) {
        synchronized (handled) {
            if (handled.contains(key)) {
                return false;
            }
            handled.add(key);
            while (handled.size() > 2000) {
                Iterator<String> iterator = handled.iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }

    private void startWorker(final int id, final URI uri, final long initialDelayMillis) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                connectionLoop(id, uri, initialDelayMillis);
            }
        }, "tdxb-ws-" + id);
        thread.setDaemon(true);
        workers.put(id, thread);
        connected.put(id, Boolean.FALSE);
        thread.start();
    }

    private void connectionLoop(int id, URI uri, long initialDelayMillis) {
        if (!sleepQuietly(initialDelayMillis)) {
            return;
        }
        long delay = reconnectDelayMillis;
        while (!Thread.currentThread().isInterrupted()) {
            RawWebSocket client = null;
            try {
                offer(Event.status(id, "正在连接：" + uri));
                client = new RawWebSocket(uri, 20000, 75000);
                client.connect();
                offer(Event.status(id, "连接成功，开始接收开奖推送。"));
                connected.put(id, Boolean.TRUE);
                delay = reconnectDelayMillis;
                while (!Thread.currentThread().isInterrupted()) {
                    String message = client.readMessage();
                    if (message != null) {
                        offer(Event.message(id, message));
                    }
                }
            } catch (Throwable error) {
                offer(Event.error(id, safeMessage(error)));
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Throwable ignored) {
                        // 关闭失败不影响下一轮重连
                    }
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            long jitterBound = Math.max(1000L, delay / 5L);
            sleepQuietly(delay + ThreadLocalRandom.current().nextLong(jitterBound + 1L));
            delay = Math.min(delay * 2L, 60000L);
        }
    }

    private void offer(Event event) {
        if (!events.offer(event)) {
            events.poll();
            events.offer(event);
        }
    }

    private URI buildUri() throws Exception {
        String input = host == null ? "" : host.trim();
        if (input.length() == 0) {
            throw new IllegalArgumentException("tdxb.ws.host 不能为空");
        }
        if (input.indexOf("://") < 0) {
            input = "ws://" + input;
        }
        URI parsed = new URI(input);
        String scheme = parsed.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            scheme = "ws";
        } else if ("https".equalsIgnoreCase(scheme)) {
            scheme = "wss";
        } else if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("不支持的地址协议：" + scheme);
        }
        if (parsed.getHost() == null) {
            throw new IllegalArgumentException("无效的数据源地址：" + host);
        }
        return new URI(scheme, parsed.getUserInfo(), parsed.getHost(), parsed.getPort(),
                "/websocket", "game_type=" + SOURCE_GAME_TYPE, null);
    }

    // ==================== 解析辅助 ====================

    private static String field(String json, String name) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(name)
                + "\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static Integer intField(String json, String name) {
        String text = field(json, name);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 把 open_time 解析成毫秒时间戳。上游可能给秒级/毫秒级时间戳，也可能给日期字符串，这里都兜住；
     * 解析不出来返回 0，日志里会保留原文供人工确认格式。
     */
    static long parseEpochMillis(String value) {
        if (value == null) {
            return 0L;
        }
        String text = value.trim();
        if (text.length() == 0) {
            return 0L;
        }
        if (isDigits(text)) {
            try {
                long number = Long.parseLong(text);
                if (text.length() <= 10) {
                    return number * 1000L;
                }
                return number;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd HH:mm", "HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(true);
                Date parsed = format.parse(text);
                if (!pattern.startsWith("yyyy")) {
                    // 只有时分秒：补上今天的日期
                    java.util.Calendar today = java.util.Calendar.getInstance();
                    java.util.Calendar hit = java.util.Calendar.getInstance();
                    hit.setTime(parsed);
                    today.set(java.util.Calendar.HOUR_OF_DAY, hit.get(java.util.Calendar.HOUR_OF_DAY));
                    today.set(java.util.Calendar.MINUTE, hit.get(java.util.Calendar.MINUTE));
                    today.set(java.util.Calendar.SECOND, hit.get(java.util.Calendar.SECOND));
                    today.set(java.util.Calendar.MILLISECOND, 0);
                    return today.getTimeInMillis();
                }
                return parsed.getTime();
            } catch (Exception ignored) {
                // 换下一个格式
            }
        }
        return 0L;
    }

    private static int missedPeriods(String previous, String current) {
        if (!isDigits(previous) || !isDigits(current) || previous.length() < 5
                || previous.length() != current.length()) {
            return 0;
        }
        int split = previous.length() - 4;
        if (!previous.substring(0, split).equals(current.substring(0, split))) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(current.substring(split))
                    - Integer.parseInt(previous.substring(split)) - 1, 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isDigits(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private static int intProp(String key, int defaultValue, int min, int max) {
        try {
            int parsed = Integer.parseInt(System.getProperty(key, String.valueOf(defaultValue)));
            if (parsed < min || parsed > max) {
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String time(long millis) {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(millis));
    }

    private static String brief(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 1024 ? text.substring(0, 1024) + "...(truncated)" : text;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return message == null || message.length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private static boolean sleepQuietly(long millis) {
        if (millis <= 0L) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private enum EventType {
        STATUS, ERROR, MESSAGE
    }

    private static final class Event {
        final EventType type;
        final int workerId;
        final String text;
        final boolean connected;

        private Event(EventType type, int workerId, String text, boolean connected) {
            this.type = type;
            this.workerId = workerId;
            this.text = text;
            this.connected = connected;
        }

        static Event status(int id, String text) {
            return new Event(EventType.STATUS, id, text, true);
        }

        static Event error(int id, String text) {
            return new Event(EventType.ERROR, id, text, false);
        }

        static Event message(int id, String text) {
            return new Event(EventType.MESSAGE, id, text, true);
        }
    }

    /** 仅用 JDK8 实现的最小 WebSocket 客户端，支持 ws/wss、分片、Ping/Pong */
    private static final class RawWebSocket {
        private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private static final int MAX_HEADER_SIZE = 65536;
        private static final int MAX_FRAME_SIZE = 2 * 1024 * 1024;
        private static final SecureRandom RANDOM = new SecureRandom();

        private final URI uri;
        private final int connectTimeout;
        private final int readTimeout;
        private Socket socket;
        private DataInputStream input;
        private OutputStream output;
        private ByteArrayOutputStream fragmented;
        private int fragmentedOpcode = -1;

        RawWebSocket(URI uri, int connectTimeout, int readTimeout) {
            this.uri = uri;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }

        void connect() throws Exception {
            boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort();
            if (port < 0) {
                port = secure ? 443 : 80;
            }

            Socket plain = new Socket();
            plain.setKeepAlive(true);
            plain.setTcpNoDelay(true);
            plain.connect(new InetSocketAddress(uri.getHost(), port), connectTimeout);

            if (secure) {
                socket = ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(plain, uri.getHost(), port, true);
            } else {
                socket = plain;
            }
            socket.setSoTimeout(readTimeout);
            input = new DataInputStream(socket.getInputStream());
            output = socket.getOutputStream();

            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String key = Base64.getEncoder().encodeToString(nonce);
            String path = uri.getRawPath();
            if (path == null || path.length() == 0) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }
            String hostHeader = uri.getHost();
            if (uri.getPort() >= 0) {
                hostHeader += ":" + uri.getPort();
            }

            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "User-Agent: game-yk-TdxbWsPoller/1.0\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();

            String headers = readHttpHeaders(input);
            String firstLine = headers.substring(0, headers.indexOf("\r\n"));
            if (!firstLine.matches("HTTP/1\\.[01] 101(?: .*)?")) {
                throw new IOException("WebSocket 握手失败：" + firstLine);
            }
            String expected = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                            (key + MAGIC).getBytes(StandardCharsets.US_ASCII)));
            if (!expected.equals(headerValue(headers, "Sec-WebSocket-Accept"))) {
                throw new IOException("WebSocket 握手校验失败");
            }
        }

        String readMessage() throws Exception {
            while (true) {
                int first;
                try {
                    first = input.readUnsignedByte();
                } catch (SocketTimeoutException timeout) {
                    throw new IOException("超过 " + (readTimeout / 1000) + " 秒未收到服务端数据", timeout);
                }
                int second = input.readUnsignedByte();
                boolean fin = (first & 0x80) != 0;
                int opcode = first & 0x0F;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7F;
                if (length == 126) {
                    length = input.readUnsignedShort();
                } else if (length == 127) {
                    length = input.readLong();
                }
                if (length < 0 || length > MAX_FRAME_SIZE) {
                    throw new IOException("WebSocket 帧过大：" + length);
                }

                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    input.readFully(mask);
                }
                byte[] payload = new byte[(int) length];
                input.readFully(payload);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                    }
                }

                if (opcode == 0x8) {
                    throw new EOFException("服务端关闭 WebSocket 连接");
                }
                if (opcode == 0x9) {
                    sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) {
                    continue;
                }
                if (opcode == 0x1 || opcode == 0x2) {
                    if (fin) {
                        return opcode == 0x1 ? new String(payload, StandardCharsets.UTF_8) : null;
                    }
                    fragmented = new ByteArrayOutputStream();
                    fragmented.write(payload);
                    fragmentedOpcode = opcode;
                    continue;
                }
                if (opcode == 0x0) {
                    if (fragmented == null) {
                        throw new IOException("收到无起始帧的 WebSocket 分片");
                    }
                    fragmented.write(payload);
                    if (fin) {
                        byte[] complete = fragmented.toByteArray();
                        int originalOpcode = fragmentedOpcode;
                        fragmented = null;
                        fragmentedOpcode = -1;
                        return originalOpcode == 0x1
                                ? new String(complete, StandardCharsets.UTF_8) : null;
                    }
                }
            }
        }

        private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
            if (output == null) {
                return;
            }
            output.write(0x80 | (opcode & 0x0F));
            int length = payload.length;
            if (length <= 125) {
                output.write(0x80 | length);
            } else if (length <= 65535) {
                output.write(0x80 | 126);
                output.write((length >>> 8) & 0xFF);
                output.write(length & 0xFF);
            } else {
                output.write(0x80 | 127);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    output.write((length >>> shift) & 0xFF);
                }
            }
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            output.write(mask);
            for (int i = 0; i < payload.length; i++) {
                output.write(payload[i] ^ mask[i % 4]);
            }
            output.flush();
        }

        void close() throws IOException {
            if (socket != null) {
                socket.close();
            }
        }

        private static String readHttpHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int matched = 0;
            int[] ending = {13, 10, 13, 10};
            while (bytes.size() < MAX_HEADER_SIZE) {
                int value = input.read();
                if (value < 0) {
                    throw new EOFException("WebSocket 握手响应提前结束");
                }
                bytes.write(value);
                if (value == ending[matched]) {
                    matched++;
                    if (matched == ending.length) {
                        return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
                    }
                } else {
                    matched = value == ending[0] ? 1 : 0;
                }
            }
            throw new IOException("WebSocket 握手响应头过大");
        }

        private static String headerValue(String headers, String name) {
            String[] lines = headers.split("\\r\\n");
            String prefix = name.toLowerCase(Locale.ROOT) + ":";
            for (String line : lines) {
                if (line.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
            return null;
        }
    }
}
