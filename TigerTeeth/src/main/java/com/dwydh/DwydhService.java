package com.dwydh;

import com.commom.RestTemplateUtils;
import com.yqlyy.GameYqlyyWsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import org.slf4j.Logger;

@Service
@Slf4j
public class DwydhService {

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    public RestTemplateUtils restTemplateUtils;

    public void init() {
        taskExecutor.execute(() -> {

            GameYqlyyWsClient client = new GameYqlyyWsClient(restTemplateUtils);
            System.out.println("[GameYDHClient]启动");
            while (true) {
                try {
                    client.report();
                    Thread.sleep(1000 * 30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

    }

}
