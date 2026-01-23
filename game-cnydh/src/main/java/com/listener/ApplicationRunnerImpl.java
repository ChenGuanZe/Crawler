package com.listener;


import com.cnydh.YyService;
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
    private YyService lfService;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        lfService.init();
        log.info("启动执行完成。。。。。。。");
    }

}
