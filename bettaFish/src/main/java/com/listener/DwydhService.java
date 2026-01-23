package com.listener;



import com.utils.DouyuOkHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class DwydhService {

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;


    public void init() {
        taskExecutor.execute(() -> {
            DouyuOkHttp.startDouyu();
        });

    }

}
