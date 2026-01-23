package com.game;

import com.game.douyu.dahsg.DhsgService;
import com.game.timer.TimerFactory;
import com.game.yk.lczh.YkLczh1Service;
import com.game.yk.yhtx.YkYhtx1Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GameRestartService {
    @Autowired
    private DhsgService dhsgService;
    @Autowired
    private YkYhtx1Service ykYhtx1Service;
    @Autowired
    private YkLczh1Service ykLczh1Service;

    public void restartValid() {
        TimerFactory.getTimer().schedule(() -> {
            try {
                log.info("线程池信息 {} {} {} {}", TimerFactory.getTimer().getPoolSize(), TimerFactory.getTimer().getActiveCount(), TimerFactory.getTimer().getTaskCount(), TimerFactory.getTimer().getCompletedTaskCount());
                long l = System.currentTimeMillis();
                l = l - 2 * 60 * 1000;
                if (dhsgService.getGetTimeInfoTimes() < l) {
                    log.info("大话三国重启");
                    try {
                        dhsgService.init();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (ykYhtx1Service.enable && ykLczh1Service.getGetTimeInfoTimes() < l) {
                    log.info("灵宠召唤重启");
                    try {
                        ykLczh1Service.init();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (ykLczh1Service.enable && ykLczh1Service.getGetTimeInfoTimes() < l) {
                    log.info("银河探险重启");
                    try {
                        ykLczh1Service.init();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                log.info("重启检测任务异常 ", e);
            }
        }, 10000, 10000);
    }
}
