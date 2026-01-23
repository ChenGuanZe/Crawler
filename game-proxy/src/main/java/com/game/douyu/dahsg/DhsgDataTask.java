package com.game.douyu.dahsg;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DhsgDataTask implements Runnable {
    private int type;
    private int times;
    private DhsgService service;

    public DhsgDataTask(int type, DhsgService service) {
        this.service = service;
        this.type = type;
    }

    public DhsgDataTask(int type, DhsgService service, int times) {
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
