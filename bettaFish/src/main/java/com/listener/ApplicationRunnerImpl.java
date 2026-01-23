package com.listener;


import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
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
        //大话三国
        dwydhService.init();

        log.info("启动执行完成。。。。。。。");
    }

}
