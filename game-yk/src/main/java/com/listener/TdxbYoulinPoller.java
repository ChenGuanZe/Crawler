package com.listener;

import com.utils.DomainNameUtil;
import com.utils.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 探岛寻宝<幽林源> 采集端，替代 {@link TdxbGamePoller}(来疯 mtop 秒级轮询) 
 */
@Slf4j
public class TdxbYoulinPoller {

    private static final String LOG_TAG = "[探岛幽林源]";

    /** 我方探岛寻宝 */
    private static final int GAME_ID = 29;
    /** 数据源侧的幽林保卫战 */
    private static final int SOURCE_GAME_ID = 23;

    private static final int CMD_INIT = 88;
    private static final int CMD_MSG = 99;
    private static final int CMD_LOGIN = 100;
    private static final int CMD_HISTORY = 110;
    private static final int CMD_RESULT = 111;
    private static final int CMD_STATUS = 113;
    private static final int CMD_HEARTBEAT = 135;

    private static final String FRAME_INIT = "1858";
    private static final String FRAME_HEARTBEAT = "188701";

    /** 数据源 monsterId -> 岛屿号(即我方 config_gwtf.clientId)，与对端实时推送里的 picUrlList 一致 */
    private static final int[] MONSTER_TO_ISLAND = {0, 2, 7, 1, 8, 5, 6, 4, 3};

    private static final String[] ISLANDS = {
            "未知岛屿", "龙鳞岛", "蓝海岛", "紫烟岛", "银月岛",
            "梦境岛", "绿洲岛", "黑石岛", "凤舞岛"
    };

    private static final long HEARTBEAT_MS = 20000L;
    /** 每期开奖后多久推下一期封盘时刻；给对端一点余量，也避免和开奖推送挤在同一毫秒 */
    private static final long TIMELINE_DELAY_MS = 2000L;
    /** 本期开奖后多久还没收到实时推送就开始拉历史兜底(实测 111 到达延迟 4.9~10.1s，正常期不该触发) */
    private static final long POLL_START_MS = 9000L;
    private static final long POLL_INTERVAL_MS = 2000L;
    /** 单期最多拉几次历史(+9s/+11s/+13s)。历史响应约 80KB，够用就行，别把它当轮询用 */
    private static final int MAX_POLLS_PER_ROUND = 3;
    /** 结果比官方开奖晚这么久才到手就只当时钟锚点，不再往游戏服推：那一期游戏侧早就判过漏单了 */
    private static final long STALE_RESULT_MS = 30000L;
    /** 与游戏侧 TdxbRoom.MIN_BET_MS 对齐：封盘时刻离现在不足 10s 会被游戏侧判定不可用而整期丢弃 */
    private static final long MIN_BET_MS = 10000L;
    private static final long RECONNECT_DELAY_MS = 3000L;
    private static final int SOCKET_CONNECT_TIMEOUT_MS = 20000;
    /** 对端每分钟至少推 2 条阶段消息 + 20s 一次心跳回包，45s 收不到东西就是连接已经死了 */
    private static final int SOCKET_READ_TIMEOUT_MS = 45000;

    private final String url = prop("tdxb.youlin.url", "ws://47.107.78.53/qmGame/game");
    private final String loginName = prop("tdxb.youlin.loginName", "测试333");
    private final String passwordMd5 = resolvePasswordMd5();
    private final int roomId = intProp("tdxb.youlin.roomId", 23, 1, 999999);
    private final boolean pushEnabled = Boolean.parseBoolean(prop("tdxb.youlin.push", "true"));
    private final long periodMs = intProp("tdxb.youlin.period", 60, 5, 600) * 1000L;
    /** 还没收到任何 servertime 时按"每分钟第几秒开奖"兜底推算时钟 */
    private final int openSecond = intProp("tdxb.youlin.openSecond", 50, 0, 59);
    private final long betLeadMs = intProp("tdxb.youlin.betLead", 0, 0, 30) * 1000L;
    private final boolean printRaw = Boolean.parseBoolean(prop("tdxb.youlin.raw", "false"));

    /** 已处理的最大期号，多路(实时+历史)收到同一期时靠它去重 */
    private volatile long lastRoundId;
    /** 上面那一期的官方开奖时刻，同时充当本地时间轴的锚点 */
    private volatile long lastRoundOpenTime;
    private volatile boolean connected;

    private int kickedCount;
    private int otherMessageLogged;

    /** 可脱离 game-yk 单独跑，验证账号/协议是否正常 */
    public static void main(String[] args) throws Exception {
        new TdxbYoulinPoller().start();
    }

    /** 阻塞运行，由 YkService 放在守护线程里调用；内部自带重连，正常不会返回 */
    public void start() throws Exception {
        log.info("{} 启动：数据源={}，房间={}，账号={}，模式={}，官方周期={}s，封盘提前={}s"
                        + " ==> 游戏侧 base_configvalue 需配 TdxbCloseSecond={}，TdxbOpenSecond={}",
                LOG_TAG, url, roomId, loginName.length() == 0 ? "未配置(仅拉历史)" : loginName,
                pushEnabled ? "推送(接管开奖)" : "观测(只打日志、不推送)",
                periodMs / 1000, betLeadMs / 1000, recommendedCloseSecond(), recommendedOpenSecond());

        long delay = RECONNECT_DELAY_MS;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                listenOnce();
                delay = RECONNECT_DELAY_MS;
            } catch (Throwable error) {
                log.warn("{} 连接中断：{}，{}ms 后重连", LOG_TAG, safeMessage(error), delay);
            }
            if (!sleepQuietly(delay + ThreadLocalRandom.current().nextLong(Math.max(1000L, delay / 5L) + 1L))) {
                return;
            }
            delay = Math.min(delay * 2L, 60000L);
        }
    }

    private void listenOnce() throws Exception {
        RawWebSocket ws = new RawWebSocket(url);
        Thread keeper = null;
        try {
            log.info("{} 正在连接 {}", LOG_TAG, url);
            ws.connect();
            ws.sendText(FRAME_INIT);
            if (loginName.length() > 0 && passwordMd5.length() > 0) {
                ws.sendText(buildLoginFrame());
            } else {
                log.warn("{} 未配置账号密码，只能靠拉历史兜底(拿不到 111 实时推送)，结果会晚几秒", LOG_TAG);
            }
            // 连上先补一次历史：马上拿到期号和 servertime，时间轴不用等到下一期开奖才对准
            ws.sendText(buildHistoryFrame());
            connected = true;
            keeper = startKeeper(ws);
            log.info("{} 连接就绪，开始接收开奖推送", LOG_TAG);

            while (connected) {
                String message = ws.readMessage();
                if (message != null) {
                    handleMessage(message.trim());
                }
            }
        } finally {
            connected = false;
            if (keeper != null) {
                keeper.interrupt();
            }
            ws.close();
        }
    }

    // ==================== 收报文 ====================

    private void handleMessage(String hex) {
        long receivedAt = System.currentTimeMillis();
        ProtoMessage message;
        try {
            message = parseProto(fromHex(hex));
        } catch (Exception error) {
            // 不静默吞掉：真出现漏期时，能一眼看出是对端没推还是我们没解开
            log.warn("{} 报文解析失败({})，长度={}，前120字符={}", LOG_TAG, safeMessage(error),
                    hex.length(), hex.length() > 120 ? hex.substring(0, 120) : hex);
            return;
        }
        if (printRaw) {
            log.info("{} 原始报文 cmd={} 长度={} 内容={}", LOG_TAG, message.command, hex.length(), brief(hex));
        }

        if (message.command == CMD_RESULT || message.command == CMD_HISTORY) {
            handleRounds(message, receivedAt);
            return;
        }
        if (message.command == CMD_MSG) {
            handleSystemMessage(message);
            return;
        }
        if (message.command == CMD_INIT || message.command == CMD_LOGIN || message.command == CMD_STATUS
                || message.command == CMD_HEARTBEAT) {
            return;
        }
        if (otherMessageLogged < 5) {
            otherMessageLogged++;
            log.info("{} 其它报文样本{} cmd={}：{}", LOG_TAG, otherMessageLogged, message.command,
                    message.jsons.isEmpty() ? "(无 field6)" : brief(message.jsons.get(0)));
        }
    }

    /**
     * 一条报文里可能带几百条历史，只取期号最大的那条。
     * <p>
     * 这点很关键：连上先拉一次历史会返回当天全部近千期，逐条推的话会把几小时前的号码
     * 当成本期结果送进游戏服；只认最新一期，才能既拿到时钟锚点又不污染开奖。
     */
    private void handleRounds(ProtoMessage message, long receivedAt) {
        Round newest = null;
        for (String json : message.jsons) {
            Round round = parseRound(json, receivedAt);
            if (round != null && (newest == null || round.id > newest.id)) {
                newest = round;
            }
        }
        if (newest == null) {
            return;
        }
        if (newest.id <= lastRoundId) {
            return;//另一路已经处理过这期了
        }
        if (lastRoundId > 0 && newest.id > lastRoundId + 1) {
            log.warn("{} 期号从 {} 跳到 {}，中间 {} 期没拿到(对端漏推或我们掉线)",
                    LOG_TAG, lastRoundId, newest.id, newest.id - lastRoundId - 1);
        }
        lastRoundId = newest.id;
        lastRoundOpenTime = newest.openTime;

        long lag = receivedAt - newest.openTime;
        log.info("{} 开奖 期号={} 号码={}({}) 官方开奖={} 到手延迟={}ms 来源={}",
                LOG_TAG, newest.id, newest.island, islandName(newest.island),
                time(newest.openTime), lag,
                message.command == CMD_RESULT ? "实时推送" : "历史兜底");

        if (!pushEnabled) {
            return;
        }
        if (lag > STALE_RESULT_MS) {
            // 刚连上/断线重连时拿到的是上一期甚至更早的号码，推过去只会进结果池等着过期，
            // 但它的 servertime 仍然是准的时钟锚点，留着给时间轴用
            log.info("{} 期号={} 已过期 {}ms，只作时钟锚点不推送开奖", LOG_TAG, newest.id, lag);
            return;
        }
        pushLottery(newest);
    }

    private void handleSystemMessage(ProtoMessage message) {
        String content = message.jsons.isEmpty() ? "" : message.jsons.get(0);
        if (content.contains("其他地方登录") || content.contains("其它地方登录")) {
            kickedCount++;
            log.error("{} 数据源账号被顶下线(第{}次)：{}。同一个源账号只能有一个采集进程，"
                            + "请检查是否还有别的 game-yk 实例或本地调试探针在用 {}，互踢会大面积漏期",
                    LOG_TAG, kickedCount, brief(content), loginName);
            return;
        }
        log.info("{} 数据源消息：{}", LOG_TAG, brief(content));
    }

    /** 解析一条开奖记录；字段缺失或号码超范围返回 null */
    private Round parseRound(String json, long receivedAt) {
        // 历史记录(110)里没有 gameId 字段，但请求时已经按 gameId=23 过滤过，缺省当本游戏处理
        if (jsonLong(json, "gameId", SOURCE_GAME_ID) != SOURCE_GAME_ID) {
            return null;
        }
        long id = jsonLong(json, "id", -1L);
        if (id <= 0L) {
            return null;
        }
        int monsterId = (int) jsonLong(json, "monsterId", -1L);
        int island = (int) jsonLong(json, "picUrlList", -1L);
        int mapped = monsterId > 0 && monsterId < MONSTER_TO_ISLAND.length ? MONSTER_TO_ISLAND[monsterId] : -1;
        if (island < 1 || island > 8) {
            island = mapped;//历史记录只有 monsterId，按对端映射表换算
        } else if (mapped > 0 && mapped != island) {
            // 对端改了怪物-岛屿对应关系。历史兜底那一路只有 monsterId，映射表错了就会开错号，必须告警
            log.error("{} 映射表与对端不一致：monsterId={} 对端给的岛屿号={} 我们算出={}，"
                            + "请核对 MONSTER_TO_ISLAND 与对端配置",
                    LOG_TAG, monsterId, island, mapped);
        }
        if (island < 1 || island > 8) {
            log.warn("{} 开奖记录号码无效：{}", LOG_TAG, brief(json));
            return null;
        }
        long openTime = jsonLong(json, "servertime", 0L);
        if (openTime <= 0L) {
            openTime = officialTimeAt(receivedAt);
            log.warn("{} 期号={} 没带 servertime，按官方时钟推算为 {}", LOG_TAG, id, time(openTime));
        }
        return new Round(id, island, openTime);
    }

    // ==================== 心跳 / 时间轴 / 兜底 ====================

    private Thread startKeeper(final RawWebSocket ws) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    keeperLoop(ws);
                } catch (Throwable error) {
                    log.warn("{} 心跳线程退出：{}，将由主线程重连", LOG_TAG, safeMessage(error));
                    connected = false;
                    ws.close();
                }
            }
        }, "tdxb-youlin-keeper");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void keeperLoop(RawWebSocket ws) throws IOException {
        long nextHeartbeat = System.currentTimeMillis() + HEARTBEAT_MS;
        long timelinePushed = 0L;
        long pollingRound = 0L;
        long lastPollAt = 0L;
        int polls = 0;

        while (connected && !Thread.currentThread().isInterrupted()) {
            if (!sleepQuietly(200L)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now >= nextHeartbeat) {
                ws.sendText(FRAME_HEARTBEAT);
                nextHeartbeat = now + HEARTBEAT_MS;
            }

            long official = officialTimeAt(now);
            if (official <= 0L) {
                continue;
            }
            if (official != pollingRound) {
                pollingRound = official;
                polls = 0;
            }

            // 时间轴保活：每期开奖后固定推一次下一期封盘时刻，与结果到没到无关。
            // 旧采集端是"收到开奖才推时间", 结果一期没收到游戏侧就取不到新封盘时刻，
            // 只能干等到超时再自己估算，玩家看到的是倒计时卡在 00:00。
            if (official > timelinePushed && now - official >= TIMELINE_DELAY_MS) {
                pushGameTime(official + periodMs);
                timelinePushed = official;
            }

            // 结果兜底：本期实时推送没来就拉历史。历史响应约 80KB，所以只在缺期时发、且限次数
            if (lastRoundOpenTime < official && now - official >= POLL_START_MS
                    && polls < MAX_POLLS_PER_ROUND && now - lastPollAt >= POLL_INTERVAL_MS) {
                ws.sendText(buildHistoryFrame());
                lastPollAt = now;
                polls++;
                log.info("{} 本期(官方开奖={})未收到实时推送，第{}次拉历史兜底", LOG_TAG, time(official), polls);
            }
        }
    }

    /**
     * now 之前最近一次官方开奖时刻。
     * <p>
     * 锚点优先用对端给的 servertime——它是对端服务器的时钟，直接跟着它走，
     * 两边机器有几秒时差也不会让封盘偏离官方开奖。一条结果都还没收到时才退回按秒位推算。
     */
    private long officialTimeAt(long now) {
        long anchor = lastRoundOpenTime;
        if (anchor > 0L) {
            if (anchor > now) {
                return anchor;//对端时钟比本机快，直接用对端的
            }
            return anchor + ((now - anchor) / periodMs) * periodMs;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, openSecond);
        c.set(Calendar.MILLISECOND, 0);
        long official = c.getTimeInMillis();
        if (official > now) {
            official -= 60000L;//秒位兜底只对每分钟一期的节奏有意义
        }
        return official;
    }

    /**
     * 推开奖号。monsterId 这个字段名是历史包袱，游戏侧 TdxbController 拿它当
     * config_gwtf.clientId 去查内部 monsterId，所以这里要给的是岛屿号(1-8)而不是对端的怪物 id。
     */
    private void pushLottery(Round round) {
        String json = "{\"monsterId\":" + round.island + ",\"code\":1,\"roundId\":" + round.id
                + ",\"openTime\":" + round.openTime + "}";
        for (String base : DomainNameUtil.urls) {
            String fullUrl = base + "/tdxb/luckyMonster";
            try {
                String resp = OkHttpUtil.postJson(fullUrl, json);
                log.info("{} 推送开奖 {} => {} 响应：{}", LOG_TAG, json, fullUrl, resp);
            } catch (Exception e) {
                log.warn("{} 推送开奖异常 {}：{}", LOG_TAG, fullUrl, e.getMessage());
            }
        }
    }

    /**
     * 推下一期封盘时刻。
     * <p>
     * 封盘 = 下一期官方开奖 - betLead，默认 betLead=0 即与官方开奖同刻。对端自己也是开奖后
     * 5s 左右才把号码广播给他们的玩家，所以同刻封盘不存在"看着结果下注"，不必像旧数据源那样
     * 大幅提前。游戏侧再等 TdxbCloseSecond 秒才判开奖，正好覆盖结果送达。
     */
    private void pushGameTime(long nextOfficialTime) {
        long closeTime = nextOfficialTime - betLeadMs;
        long now = System.currentTimeMillis();
        while (closeTime - now < MIN_BET_MS) {
            // 游戏侧要求封盘时刻至少在 10s 之后，否则整个时刻会被丢弃、投注倒计时归零
            log.warn("{} 算出的封盘时刻 {} 距现在不足 {}s，顺延一期", LOG_TAG, time(closeTime), MIN_BET_MS / 1000);
            closeTime += periodMs;
        }
        for (String base : DomainNameUtil.transitUrls) {
            String fullUrl = base + "/gameProxy/proxy/setGameTime?gameId=" + GAME_ID + "&time=" + closeTime;
            try {
                String resp = OkHttpUtil.get(fullUrl, null);
                log.info("{} 推送封盘时刻 {}({})，游戏应在 {} 判开奖 => 响应：{}", LOG_TAG, closeTime,
                        time(closeTime), time(closeTime + recommendedCloseSecond() * 1000L), resp);
            } catch (Exception e) {
                log.warn("{} 推送封盘时刻异常 {}：{}", LOG_TAG, fullUrl, e.getMessage());
            }
        }
    }

    /** 游戏侧该配的 TdxbCloseSecond：封盘到开奖判定要盖住"封盘提前量 + 结果送达" */
    private long recommendedCloseSecond() {
        return betLeadMs / 1000L + 8L;
    }

    /** 游戏侧该配的 TdxbOpenSecond：开奖判定落在每分钟第几秒 */
    private long recommendedOpenSecond() {
        return (openSecond + recommendedCloseSecond() - betLeadMs / 1000L) % 60L;
    }

    // ==================== 组包 / 解包 ====================

    private String buildLoginFrame() {
        String json = "{\"loginName\":\"" + escapeJson(loginName) + "\",\"loginPsw\":\""
                + passwordMd5 + "\",\"channelId\":1001}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarint(packet, (2 << 3) | 2);
        writeVarint(packet, body.length);
        packet.write(body, 0, body.length);
        writeVarint(packet, 3 << 3);
        writeVarint(packet, CMD_LOGIN);
        return toHex(packet.toByteArray());
    }

    private String buildHistoryFrame() {
        String json = "{\"gameId\":" + SOURCE_GAME_ID + ",\"roomId\":" + roomId + "}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarint(packet, 3 << 3);
        writeVarint(packet, CMD_HISTORY);
        writeVarint(packet, (7 << 3) | 2);
        writeVarint(packet, body.length);
        packet.write(body, 0, body.length);
        return toHex(packet.toByteArray());
    }

    /** 只取需要的两个字段：field3=命令号，field6=业务 json(可重复)，其余按 wire type 跳过 */
    private static ProtoMessage parseProto(byte[] data) throws IOException {
        int position = 0;
        int command = -1;
        List<String> jsons = new ArrayList<String>();
        while (position < data.length) {
            long[] keyRead = readVarint(data, position);
            long key = keyRead[0];
            position = (int) keyRead[1];
            int field = (int) (key >> 3);
            int wireType = (int) (key & 7);
            if (wireType == 0) {
                long[] valueRead = readVarint(data, position);
                if (field == 3) {
                    command = (int) valueRead[0];
                }
                position = (int) valueRead[1];
            } else if (wireType == 2) {
                long[] sizeRead = readVarint(data, position);
                int size = (int) sizeRead[0];
                position = (int) sizeRead[1];
                int end = position + size;
                if (size < 0 || end < position || end > data.length) {
                    throw new IOException("protobuf 长度非法：" + size);
                }
                if (field == 6) {
                    jsons.add(new String(data, position, size, StandardCharsets.UTF_8));
                }
                position = end;
            } else if (wireType == 1) {
                position += 8;
            } else if (wireType == 5) {
                position += 4;
            } else {
                throw new IOException("不支持的 protobuf wire type：" + wireType);
            }
            if (position > data.length) {
                throw new IOException("protobuf 字段被截断");
            }
        }
        return new ProtoMessage(command, jsons);
    }

    private static long[] readVarint(byte[] data, int position) throws IOException {
        long value = 0L;
        for (int shift = 0; position < data.length && shift < 64; shift += 7) {
            int current = data[position++] & 0xff;
            value |= (long) (current & 0x7f) << shift;
            if ((current & 0x80) == 0) {
                return new long[]{value, position};
            }
        }
        throw new IOException("protobuf varint 非法");
    }

    private static void writeVarint(ByteArrayOutputStream output, long value) {
        while ((value & ~0x7fL) != 0L) {
            output.write((int) ((value & 0x7f) | 0x80));
            value >>>= 7;
        }
        output.write((int) value);
    }

    // ==================== 小工具 ====================

    /** 数字字段可能带引号(如 "picUrlList":"1")，两种都认 */
    private static long jsonLong(String json, String name, long fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name)
                + "\"\\s*:\\s*(?:\"(-?\\d+)\"|(-?\\d+))").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Long.parseLong(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String resolvePasswordMd5() {
        String md5 = prop("tdxb.youlin.passwordMd5", "").trim().toLowerCase(Locale.ROOT);
        if (md5.length() > 0) {
            return md5.matches("[0-9a-f]{32}") ? md5 : "";
        }
        String plain = prop("tdxb.youlin.password", "qwer1234");
        if (plain.length() == 0) {
            return "";
        }
        try {
            return toHex(MessageDigest.getInstance("MD5").digest(plain.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception error) {
            log.error("{} 密码取 md5 失败", LOG_TAG, error);
            return "";
        }
    }

    private static String prop(String key, String defaultValue) {
        String value = System.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }

    private static int intProp(String key, int defaultValue, int min, int max) {
        try {
            int parsed = Integer.parseInt(prop(key, String.valueOf(defaultValue)));
            return parsed < min || parsed > max ? defaultValue : parsed;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String islandName(int island) {
        return island >= 1 && island <= 8 ? ISLANDS[island] : ISLANDS[0];
    }

    private static String time(long millis) {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(millis));
    }

    private static String brief(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "...(共" + text.length() + ")" : text;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
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

    private static String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(String.format("%02X", value & 0xff));
        }
        return output.toString();
    }

    private static byte[] fromHex(String value) {
        if (value.length() == 0 || (value.length() & 1) != 0) {
            throw new IllegalArgumentException("十六进制串长度非法：" + value.length());
        }
        byte[] data = new byte[value.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }

    private static final class Round {
        final long id;
        final int island;
        final long openTime;

        Round(long id, int island, long openTime) {
            this.id = id;
            this.island = island;
            this.openTime = openTime;
        }
    }

    private static final class ProtoMessage {
        final int command;
        final List<String> jsons;

        ProtoMessage(int command, List<String> jsons) {
            this.command = command;
            this.jsons = jsons;
        }
    }

    /** 只用 JDK8 标准库实现的最小 WebSocket 客户端，支持 ws/wss、分片、Ping/Pong */
    private static final class RawWebSocket {
        private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private static final int MAX_HEADER_SIZE = 65536;
        private static final int MAX_FRAME_SIZE = 4 * 1024 * 1024;
        private static final SecureRandom RANDOM = new SecureRandom();

        private final URI uri;
        private final Object writeLock = new Object();
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private ByteArrayOutputStream fragments;
        private int fragmentOpcode = -1;

        RawWebSocket(String url) throws Exception {
            this.uri = new URI(url);
        }

        void connect() throws Exception {
            boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
            if (!secure && !"ws".equalsIgnoreCase(uri.getScheme())) {
                throw new IOException("地址必须是 ws:// 或 wss://，当前=" + uri);
            }
            int port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : 80);
            SocketFactory factory = secure ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
            socket = factory.createSocket();
            socket.connect(new InetSocketAddress(uri.getHost(), port), SOCKET_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            input = socket.getInputStream();
            output = socket.getOutputStream();

            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String key = Base64.getEncoder().encodeToString(nonce);
            String path = uri.getRawPath();
            if (path == null || path.length() == 0) {
                path = "/";
            }
            if (uri.getRawQuery() != null && uri.getRawQuery().length() > 0) {
                path += "?" + uri.getRawQuery();
            }
            String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Origin: " + (secure ? "https://" : "http://") + host + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "User-Agent: game-yk-TdxbYoulinPoller/1.0\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();

            String headers = readHeaders();
            String firstLine = headers.substring(0, Math.max(headers.indexOf("\r\n"), 0));
            if (!firstLine.matches("HTTP/1\\.[01] 101(?: .*)?")) {
                throw new IOException("WebSocket 握手失败：" + firstLine);
            }
            String expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + MAGIC).getBytes(StandardCharsets.US_ASCII)));
            if (!expected.equals(headerValue(headers, "sec-websocket-accept"))) {
                throw new IOException("WebSocket 握手校验失败");
            }
        }

        /** 文本帧返回字符串，二进制帧返回 null(对端只发文本，出现二进制直接忽略) */
        String readMessage() throws IOException {
            while (true) {
                int first = readByte();
                int second = readByte();
                boolean finished = (first & 0x80) != 0;
                int opcode = first & 0x0f;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7f;
                if (length == 126) {
                    length = readUnsignedShort();
                } else if (length == 127) {
                    length = readLong();
                }
                if (length < 0 || length > MAX_FRAME_SIZE) {
                    throw new IOException("WebSocket 帧过大：" + length);
                }
                byte[] mask = masked ? readExact(4) : null;
                byte[] payload = readExact((int) length);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                }

                if (opcode == 0x8) {
                    throw new EOFException("对端关闭了 WebSocket 连接");
                }
                if (opcode == 0x9) {
                    sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) {
                    continue;
                }
                if (opcode == 0x1 || opcode == 0x2) {
                    if (finished) {
                        return opcode == 0x1 ? new String(payload, StandardCharsets.UTF_8) : null;
                    }
                    fragments = new ByteArrayOutputStream();
                    fragments.write(payload, 0, payload.length);
                    fragmentOpcode = opcode;
                    continue;
                }
                if (opcode == 0x0) {
                    if (fragments == null) {
                        throw new IOException("收到无起始帧的 WebSocket 分片");
                    }
                    fragments.write(payload, 0, payload.length);
                    if (finished) {
                        byte[] complete = fragments.toByteArray();
                        int originalOpcode = fragmentOpcode;
                        fragments = null;
                        fragmentOpcode = -1;
                        return originalOpcode == 0x1 ? new String(complete, StandardCharsets.UTF_8) : null;
                    }
                }
            }
        }

        void sendText(String value) throws IOException {
            sendFrame(0x1, value.getBytes(StandardCharsets.UTF_8));
        }

        private void sendFrame(int opcode, byte[] payload) throws IOException {
            synchronized (writeLock) {
                if (output == null) {
                    throw new IOException("WebSocket 未连接");
                }
                ByteArrayOutputStream header = new ByteArrayOutputStream();
                header.write(0x80 | opcode);
                int length = payload.length;
                if (length <= 125) {
                    header.write(0x80 | length);
                } else if (length <= 65535) {
                    header.write(0x80 | 126);
                    header.write((length >>> 8) & 0xff);
                    header.write(length & 0xff);
                } else {
                    header.write(0x80 | 127);
                    for (int i = 7; i >= 0; i--) {
                        header.write((int) ((length >>> (8 * i)) & 0xff));
                    }
                }
                byte[] mask = new byte[4];
                RANDOM.nextBytes(mask);
                header.write(mask, 0, 4);
                output.write(header.toByteArray());
                byte[] masked = new byte[payload.length];
                for (int i = 0; i < payload.length; i++) {
                    masked[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
                output.write(masked);
                output.flush();
            }
        }

        void close() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
                // 关闭失败不影响下一轮重连
            } finally {
                socket = null;
                input = null;
                output = null;
            }
        }

        private String readHeaders() throws IOException {
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
            String prefix = name.toLowerCase(Locale.ROOT) + ":";
            for (String line : headers.split("\\r?\\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
            return null;
        }

        private int readByte() throws IOException {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("对端关闭了连接");
            }
            return value;
        }

        private int readUnsignedShort() throws IOException {
            return (readByte() << 8) | readByte();
        }

        private long readLong() throws IOException {
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | readByte();
            }
            return value;
        }

        private byte[] readExact(int size) throws IOException {
            byte[] data = new byte[size];
            int offset = 0;
            while (offset < size) {
                int read = input.read(data, offset, size - offset);
                if (read < 0) {
                    throw new EOFException("对端关闭了连接");
                }
                offset += read;
            }
            return data;
        }
    }
}
