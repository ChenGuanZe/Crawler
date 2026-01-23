package com.game.ympd.lczh;


public class LczhDataTask implements Runnable {
    private int type;
    private int times;
    private LczhService service;

    public LczhDataTask(int type, LczhService service) {
        this.service = service;
        this.type = type;
    }

    public LczhDataTask(int type, LczhService service, int times) {
        this.service = service;
        this.type = type;
        this.times = times;
    }

    @Override
    public void run() {
        if (type == 1) {
            try {
                service.gameInfo();
            }catch (Exception e){
                e.printStackTrace();
            }
        } else if (type == 2) {
            try {
                service.luckMonster(this.times);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
