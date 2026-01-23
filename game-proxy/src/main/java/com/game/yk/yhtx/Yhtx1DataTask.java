package com.game.yk.yhtx;

import com.game.ympd.yhtx.YhtxService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Yhtx1DataTask implements Runnable {
    private int type;
    private int times;
    private YkYhtx1Service service;

    public Yhtx1DataTask(int type, YkYhtx1Service service) {
        this.service = service;
        this.type = type;
    }

    public Yhtx1DataTask(int type, YkYhtx1Service service, int times) {
        this.service = service;
        this.type = type;
        this.times = times;
    }

    @Override
    public void run() {
        if (type == 1) {
            try {
                service.gameInfo();
            } catch (Exception e) {
                log.error("系统异常 {}", e);
                e.printStackTrace();
            }
        } else if (type == 2) {
            try {
                service.luckMonster(this.times);
            } catch (Exception e) {
                log.error("系统异常 {}", e);
                e.printStackTrace();
            }
        }
    }
}
