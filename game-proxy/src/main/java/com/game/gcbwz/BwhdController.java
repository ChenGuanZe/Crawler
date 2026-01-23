package com.game.gcbwz;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.redis.RedisService;
import com.game.ylbwz.YlbwzWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.net.HttpCookie;

@Slf4j
@RestController
@RequestMapping("/gcbwz")
public class BwhdController {

    @Resource
    private RedisService redisService;
    @Value("${manxiang.session.gcbwz}")
    private String gcSession;

    @GetMapping(value = "/lastLucky")
    public String lastLucky() {
        Long stime = System.currentTimeMillis();
        log.info("古城-获取历史数据请求参数：");
        String resp = getList();
        if (resp.contains("登录已失效")) {
            resp = getList();
        }

        JSONObject json = JSONObject.parseObject(resp);
        JSONObject data = json.getJSONArray("data").getJSONObject(0);
        log.info("古城-获取历史数据请求响应：{} || 耗时：{}", data, System.currentTimeMillis() - stime);
        return data.toJSONString();
    }

    @GetMapping(value = "/luckyList")
    public String luckyList() {
        Long stime = System.currentTimeMillis();
        log.info("古城-获取所有历史数据请求参数：");
        String resp = getList();
        if (resp.contains("登录已失效")) {
            resp = getList();
        }
        log.info("古城-获取所有历史数据请求响应：{} || 耗时：{}", System.currentTimeMillis() - stime);
        return resp;
    }

    private String getList() {
        HttpCookie httpCookie = new HttpCookie("SESSION", gcSession);
        HttpRequest cookie = HttpUtil.createGet("http://tulong.maxxiang.com:8003/towerDefence/monster/moribynum?num=3000&group=1").cookie(httpCookie);
        HttpResponse execute = cookie.execute();
        String resp = execute.body();
        return resp;
    }

    @GetMapping(value = "/luckyTime")
    public String luckyTime() {
        log.info("古城-获取开奖时间请求");
        Long time = redisService.getCacheObject("lottery_open_time_gc");
        log.info("古城-获取开奖时间请求响应：{}",time);
        return time + "";
    }
}
