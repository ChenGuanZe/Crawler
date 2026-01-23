package com.game.ympd.yhtx;


import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.RestTemplateUtils;
import com.game.redis.RedisService;
import com.game.timer.TimerFactory;
import com.game.utils.DomainNameUtil;
import com.game.ympd.YmpdService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class YhtxService {

    private String openIndex = "期号"; // 期号
    @Value("${ympd.domain}")
    private String ymzxDomain;
    @Autowired
    private YmpdService ympdService;
    @Autowired
    private RedisService redisService;
    private long getTimeInfoTimes = 0;

    public void init() throws InterruptedException {
        this.gameInfo();
        log.info("银河探险 初始化成功");
    }

    public void gameInfo() throws InterruptedException {
        this.getTimeInfoTimes = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("lotteryId", 15);
        HttpResponse execute = HttpUtil.createPost(ymzxDomain + "/game/lottery/GetIndex")
                .header("token", ympdService.getToken())
                .form("lotteryId", 15).execute();
        log.info("元梦之星-银河探险-游戏信息请求 返回参数 {}", execute.body());
        if (execute.isOk() && StringUtils.isNotBlank(execute.body())) {
            JSONObject result = JSONObject.parseObject(execute.body());
            int success = result.getIntValue("success");
            if (success == 1) {
                JSONObject info = result.getJSONObject("info");
                Long openTime = info.getLong("openTime");
                String openIndex = info.getString("openIndex");
                if (Objects.equals(openIndex, this.openIndex)) {
                    Thread.sleep(1000L);
                    this.gameInfo();
                    return;
                }
                this.openIndex = openIndex;
                Date nextDate = new Date(openTime);
                TimerFactory.getTimer().schedule(new YhtxDataTask(2, this, 0), nextDate);
                redisService.setCacheObject("gameInfo:yhtx", info.toJSONString());
            } else {
                log.info("元梦之星-银河探险-游戏信息请求失败 请求参数 {}", JSONObject.toJSONString(data));
                TimerFactory.getTimer().schedule(new YhtxDataTask(1, this), 1000L);
            }
        } else {
            TimerFactory.getTimer().schedule(new YhtxDataTask(1, this), 1000L);
        }
    }

    public void luckMonster(int times) throws InterruptedException {
        if (times > 18) {
            this.gameInfo();
        } else {
            boolean successStatus = false;
            Map<String, Object> data = new HashMap<>();
            data.put("lotteryId", 15);
            data.put("index", this.openIndex);
            HttpResponse execute = HttpUtil.createPost(ymzxDomain + "/game/lottery/GetOpenNumber")
                    .header("token", ympdService.getToken())
                    .form(data).execute();
            log.info("元梦之星-银河探险-开奖结果请求 {} 返回参数 {}", times, execute.body());
            if (execute.isOk() && StringUtils.isNotBlank(execute.body())) {
                JSONObject result = JSONObject.parseObject(execute.body());
                int success = result.getIntValue("success");
                if (success == 1) {
                    JSONObject info = result.getJSONObject("info");
                    String number = info.getString("Number");
                    if (StringUtils.isNotBlank(number) && !Objects.equals("x", number.toLowerCase())) {



                        for (int i = 0; i <  DomainNameUtil.urls.length; i++) {
                            String url=DomainNameUtil.urls[i]+"/yhtx/luckyMonster";
                            try {
                                ResponseEntity<String> responseEntity = RestTemplateUtils.post(url, info.toJSONString(), String.class);
                                String resp = responseEntity.getBody();
                                log.info(url + "-元梦之星-银河探险-开奖结果同步请求响应：{}", resp);
                            } catch (RestClientException e) {
                                log.info(url + "-元梦之星-银河探险-开奖结果同步请求异常：{}", e.getMessage());
                            }
                        }
                        successStatus = true;
                        TimerFactory.getTimer().schedule(new YhtxDataTask(1, this), 200);
                    }

                } else {
                    log.info("元梦之星-银河探险-开奖结果请求失败 请求参数 {}", JSONObject.toJSONString(data));
                }
            } else {
                log.info("元梦之星-银河探险-开奖结果请求失败 请求参数 {}", JSONObject.toJSONString(data));
            }
            if (!successStatus) {
                times++;
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.info("异常 {}",e);
                } finally {
                    this.luckMonster(times);
                }
            }
        }

    }

    public long getGetTimeInfoTimes() {
        return getTimeInfoTimes;
    }


}
