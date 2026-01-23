package com.game.ympd.yhtx;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YhtxDataTask implements Runnable {
    private int type;
    private int times;
    private YhtxService service;

    public YhtxDataTask(int type, YhtxService service) {
        this.service = service;
        this.type = type;
    }

    public YhtxDataTask(int type, YhtxService service,int times) {
        this.service = service;
        this.type = type;
        this.times = times;
    }

    @Override
    public void run() {
        if (type == 1) {
            try {
                service.gameInfo();
            } catch (InterruptedException e) {
                log.error("系统异常 {}",e);
                e.printStackTrace();
                try {
                    service.gameInfo();
                } catch (InterruptedException ex) {
                    log.error("系统异常 {}",ex);
                    ex.printStackTrace();
                }
            }
        } else if (type == 2) {
            try {
                service.luckMonster(this.times);
            } catch (InterruptedException e) {
                log.error("系统异常 {}",e);
                e.printStackTrace();
                try {
                    service.luckMonster(this.times);
                } catch (InterruptedException ex) {
                    log.error("系统异常 {}",ex);
                    ex.printStackTrace();
                }
            }
        }
    }
}
