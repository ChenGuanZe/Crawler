package com.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@Slf4j
public class LfService {

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);  // 后台线程
            t.setName("探岛寻宝<来疯直播>-scheduler");
            return t;
        });

        // ⭐ 真正异步执行你的 while(true) 任务
        scheduler.submit(() -> {
            Game1009HttpClient client = new Game1009HttpClient();
            log.info("异步任务启动：探岛寻宝<来疯直播>");

            while (true) {
                try {
                    client.trySync();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.error("异步任务异常：", e);
                }
            }
        });

    }
}
