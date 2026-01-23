package com.game;

import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.timer.TimerFactory;

import java.net.HttpCookie;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Test {


    public static void main(String[] args) throws ParseException {
        /** ------- 古城 --------- **/
//        Long stime = System.currentTimeMillis();
//        HttpCookie httpCookie = new HttpCookie("SESSION", "a74a10af-6578-4993-b0ef-6f5309ed1fcc");
//        HttpRequest cookie = HttpUtil.createGet("http://tulong.maxxiang.com/towerDefence/monster/moribynum?num=3000&group=1").cookie(httpCookie);
//        HttpResponse execute = cookie.execute();
//        String resp = execute.body();
//        System.out.println(resp);
//        System.out.println(System.currentTimeMillis() - stime);

        /** ------- 唱唱 --------- **/
//        Long stime = System.currentTimeMillis();
//        HttpCookie httpCookie = new HttpCookie("SESSION", "12b852b4-8a56-47a2-ad3d-b69cf3914488");
//                HttpRequest cookie = HttpUtil.createGet("http://changchang.maxxiang.com/changchang/monster/bynum?num=2000&group=a").cookie(httpCookie);
////        HttpRequest cookie = HttpUtil.createGet("http://changchang.maxxiang.com/changchang/monster/bynum1?num=2000&group=a").cookie(httpCookie);
//        HttpResponse execute = cookie.execute();
//        String resp = execute.body();
//        System.out.println(resp);
//        System.out.println(System.currentTimeMillis() - stime);

//        long startTime = System.currentTimeMillis();
//        System.out.println(System.currentTimeMillis());
//        JSONObject json = new JSONObject();
//        json.put("timestamp", "1721274895");
//        long l = System.currentTimeMillis() - (1721274895L*1000);
//        System.out.println(startTime- json.getLong("timestamp") * 1000);
//        System.out.println(l);
          SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date gameDateObj = sdf.parse("2025-08-06 17:23:25");
        Date nextDate = DateUtil.offsetSecond(gameDateObj, 60 - 10);
        System.out.println(nextDate.getTime());

    }
}
