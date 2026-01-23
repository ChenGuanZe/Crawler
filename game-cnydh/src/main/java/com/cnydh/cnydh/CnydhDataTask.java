package com.cnydh.cnydh;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CnydhDataTask implements Runnable {
    private int type;
    private int times;
    private CnydhService service;

    public CnydhDataTask(int type, CnydhService service) {
        this.service = service;
        this.type = type;
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
                service.luckMonster();
            } catch (Exception e) {
                log.error("系统异常 {}", e);
                e.printStackTrace();
            }
        }
    }
}
