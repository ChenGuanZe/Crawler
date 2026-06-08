package com.dwydh;

import com.commom.RestTemplateUtils;
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

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    public RestTemplateUtils restTemplateUtils;

    public void init() {
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

    }

}
