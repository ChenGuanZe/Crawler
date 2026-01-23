package com.game.yk.lczh;

import com.game.yk.yhtx.YkYhtx1Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Lczh1DataTask implements Runnable {
    private int type;
    private int times;
    private YkLczh1Service service;

    public Lczh1DataTask(int type, YkLczh1Service service) {
        this.service = service;
        this.type = type;
    }

    public Lczh1DataTask(int type, YkLczh1Service service, int times) {
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
