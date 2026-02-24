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
        scheduler = Executors.newScheduledThreadPool(2, r -> {
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
    }
}
