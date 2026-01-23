package com.listener;

import com.dwydh.DwydhService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 应用程序启动时运行程序 impl
 *
 * @author 哈哈.唐
 * @date 2024-07-10 16:23
 */
@Slf4j
@Component
public class ApplicationRunnerImpl implements ApplicationRunner {
    @Resource
    private DwydhService dwydhService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        //宠物马拉松
        dwydhService.init();

        log.info("启动执行完成。。。。。。。");
    }

}
