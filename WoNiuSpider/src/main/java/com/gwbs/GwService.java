package com.gwbs;

import com.commom.RestTemplateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class GwService {

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    public RestTemplateUtils restTemplateUtils;

    public void init() {
        taskExecutor.execute(() -> {

            GwbsMonsterHunterClient client = new GwbsMonsterHunterClient(restTemplateUtils);
            System.out.println("[GwMonsterHunterClient]启动");
            while (true) {
                try {
                    client.report();
                    Thread.sleep(1000 * 3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

    }

}
