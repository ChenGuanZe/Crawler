package com.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@Slf4j
public class YkService {

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(5, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // 启动灵宠召唤轮询任务
        scheduler.submit(() -> {
            Thread.currentThread().setName("灵宠召唤-scheduler");
            LczhGamePoller poller = new LczhGamePoller();
            log.info("异步任务启动：灵宠召唤");

            while (true) {
                try {
                    poller.trySync();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.error("灵宠召唤 - 异步任务异常：", e);
                }
            }
        });

        // 启动银河探险轮询任务
        scheduler.submit(() -> {
            Thread.currentThread().setName("银河探险-scheduler");
            YhtxGamePoller poller = new YhtxGamePoller();
            log.info("异步任务启动：银河探险");

            while (true) {
                try {
                    poller.trySync();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.error("银河探险 - 异步任务异常：", e);
                }
            }
        });

        // 启动探岛寻宝轮询任务
//        scheduler.submit(() -> {
//            Thread.currentThread().setName("探岛寻宝-scheduler");
//            TdxbGamePoller poller = new TdxbGamePoller();
//            log.info("异步任务启动：探岛寻宝");
//
//            while (true) {
//                try {
//                    poller.trySync();
//                    Thread.sleep(1000);
//                } catch (Exception e) {
//                    log.error("探岛寻宝 - 异步任务异常：", e);
//                }
//            }
//        });

        // 启动探岛寻宝采集（幽林源）。已替换掉上面的 mtop 轮询(TdxbGamePoller)和 8.212 的
        // kaijiang 推送(TdxbWsPoller)：新源自带官方开奖时刻和期号，且只晚 5s 到，投注窗口能放到 47s。
        // 三者只能有一个在跑，同时跑会互相覆盖 setGameTime 和开奖号。
        scheduler.submit(() -> {
            Thread.currentThread().setName("探岛寻宝-scheduler");
            log.info("异步任务启动：探岛寻宝(幽林源)");
            while (true) {
                try {
                    new TdxbYoulinPoller().start();
                    log.warn("探岛寻宝(幽林源) - start() 返回，5秒后重启采集");
                    Thread.sleep(5000);
                } catch (Exception e) {
                    log.error("探岛寻宝(幽林源) - 异步任务异常：", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });

        // 启动九法降妖采集任务（酷狗，精准计时，内部自带死循环，异常自愈后重进 start）
        scheduler.submit(() -> {
            Thread.currentThread().setName("九法降妖-scheduler");
            log.info("异步任务启动：九法降妖");
            while (true) {
                try {
                    new JfxyGamePoller().start();
                    log.warn("九法降妖 - start() 返回，5秒后重启采集");
                    Thread.sleep(5000);
                } catch (Exception e) {
                    log.error("九法降妖 - 异步任务异常：", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }
}
