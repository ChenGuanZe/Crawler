package com.cnydh;

import com.cnydh.cnydh.CnydhService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class YyService {
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Autowired
    private CnydhService cnydhService;
    private final String classCode = "1006";

    public void init() {
        taskExecutor.execute(() -> {
            try {
                cnydhService.init();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }



}
