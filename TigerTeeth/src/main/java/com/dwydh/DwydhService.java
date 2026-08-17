package com.dwydh;

import com.commom.RestTemplateUtils;
import com.lmyy.GameLmyyWsClient;
import com.shmj.GameShmjWsClient;
import com.yqlyy.GameYqlyyWsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class DwydhService {

    /** 多久没有收到任何开奖类消息(7109/7103)就认为虎牙订阅失效，强制重连 */
    private static final long OPEN_MSG_STALE_MS = 5 * 60 * 1000L;
    /** 多久没有收到任何 binaryMessage 就认为整个连接卡死，强制重连 */
    private static final long ANY_MSG_STALE_MS = 60 * 1000L;

    /** 浪漫约会订阅活跃度检测：单局 135s（120 下注 + 15 开奖），给 ~5 倍冗余 = 12 分钟 */
    private static final long LMYY_OPEN_MSG_STALE_MS = 12 * 60 * 1000L;
    /** 浪漫约会任意消息卡死阈值，跟马拉松一样 60s */
    private static final long LMYY_ANY_MSG_STALE_MS = 60 * 1000L;

    /** 深海迷境订阅活跃度检测：单局 ~50s 下注 + ~5s 结算 = 55s，给 ~10 倍冗余 = 10 分钟 */
    private static final long SHMJ_OPEN_MSG_STALE_MS = 10 * 60 * 1000L;
    /** 深海迷境任意消息卡死阈值：60s（来连接平台心跳频率高） */
    private static final long SHMJ_ANY_MSG_STALE_MS = 60 * 1000L;

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    public RestTemplateUtils restTemplateUtils;

    public void init() {
        // 一千零一夜 / 宠物马拉松（共用一条 WS）
        taskExecutor.execute(() -> {

            GameYqlyyWsClient client = new GameYqlyyWsClient(restTemplateUtils);
            log.info("[GameYDHClient]启动");
            while (true) {
                try {
                    if (client.isNeedReconnect()) {
                        log.info("[<虎牙>]检测到断连，立即重连...");
                        client.clearReconnectFlag();
                        Thread.sleep(3000);
                        client.report();
                    } else {
                        long openStale = client.getMsSinceLastOpenMessage();
                        long anyStale = client.getMsSinceLastAnyMessage();

                        if (anyStale > ANY_MSG_STALE_MS) {
                            // 连任何消息(包括心跳)都收不到，连接卡死
                            client.forceReconnect("连续 " + anyStale + "ms 无任何消息");
                        } else if (openStale > OPEN_MSG_STALE_MS) {
                            // 心跳/普通消息能收到，但开奖类(7109/7103)消息长时间没来
                            // 通常是虎牙端订阅状态丢失/token 过期，需要重连后重新订阅
                            client.forceReconnect("连续 " + openStale + "ms 无开奖消息(7109/7103)，疑似订阅失效");
                        } else {
                            client.report();
                        }

                        Thread.sleep(1000 * 5);
                    }
                } catch (InterruptedException e) {
                    log.error("[GameYDHClient]线程中断", e);
                } catch (Exception e) {
                    log.error("[GameYDHClient]异常，3秒后重试", e);
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        });

        // 浪漫约会（独立 WS，连不同的虎牙服务器分片）
        taskExecutor.execute(() -> {
            GameLmyyWsClient lmyyClient = new GameLmyyWsClient(restTemplateUtils);
            log.info("[GameLmyyClient]启动");
            while (true) {
                try {
                    if (lmyyClient.isNeedReconnect()) {
                        log.info("[浪漫约会<虎牙>]检测到断连，立即重连...");
                        lmyyClient.clearReconnectFlag();
                        Thread.sleep(3000);
                        lmyyClient.report();
                    } else {
                        long openStale = lmyyClient.getMsSinceLastOpenMessage();
                        long anyStale = lmyyClient.getMsSinceLastAnyMessage();

                        if (anyStale > LMYY_ANY_MSG_STALE_MS) {
                            lmyyClient.forceReconnect("连续 " + anyStale + "ms 无任何消息");
                        } else if (openStale > LMYY_OPEN_MSG_STALE_MS) {
                            lmyyClient.forceReconnect("连续 " + openStale + "ms 无开奖消息，疑似订阅失效");
                        } else {
                            lmyyClient.report();
                        }

                        Thread.sleep(1000 * 5);
                    }
                } catch (InterruptedException e) {
                    log.error("[GameLmyyClient]线程中断", e);
                } catch (Exception e) {
                    log.error("[GameLmyyClient]异常，3秒后重试", e);
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        });

        // 深海迷境（独立 WS，连来连接 lailianjie 平台）
        taskExecutor.execute(() -> {
            GameShmjWsClient shmjClient = new GameShmjWsClient(restTemplateUtils);
            log.info("[GameShmjClient]启动");
            while (true) {
                try {
                    if (shmjClient.isNeedReconnect()) {
                        log.info("[深海迷境<来连接>]检测到断连，立即重连...");
                        shmjClient.clearReconnectFlag();
                        Thread.sleep(3000);
                        shmjClient.report();
                    } else {
                        long openStale = shmjClient.getMsSinceLastOpenMessage();
                        long anyStale = shmjClient.getMsSinceLastAnyMessage();

                        if (anyStale > SHMJ_ANY_MSG_STALE_MS) {
                            shmjClient.forceReconnect("连续 " + anyStale + "ms 无任何消息");
                        } else if (openStale > SHMJ_OPEN_MSG_STALE_MS) {
                            shmjClient.forceReconnect("连续 " + openStale + "ms 无开奖消息，疑似订阅失效");
                        } else {
                            shmjClient.report();
                        }

                        Thread.sleep(1000 * 5);
                    }
                } catch (InterruptedException e) {
                    log.error("[GameShmjClient]线程中断", e);
                } catch (Exception e) {
                    log.error("[GameShmjClient]异常，3秒后重试", e);
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        });

    }

}
