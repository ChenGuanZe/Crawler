package com.game.yk.yhtx;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.RestTemplateUtils;
import com.game.redis.RedisService;
import com.game.timer.TimerFactory;
import com.game.utils.DomainNameUtil;
import com.game.yk.YkService;
import com.game.ympd.lczh.LczhDataTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class YkYhtx1Service {
    private Integer gameCode = 11;
    private long getTimeInfoTimes = 0;
    private String openIndex = "期号"; // 期号
    public boolean enable = false;
    @Autowired
    private YkService ykService;
    @Autowired
    private RedisService redisService;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    public void init() throws Exception {
        this.gameInfo();
        this.enable = true;
        log.info("银河探险 初始化成功");
    }

    public void gameInfo() throws Exception {
        this.getTimeInfoTimes = System.currentTimeMillis();
        JSONObject result = ykService.requestData(null, gameCode,"银河探险-游戏信息");
        if (Objects.nonNull(result)) {
            JSONObject resultData = result.getJSONObject("data");
            String gameDate = resultData.getString("gameDate");
            String openIndex = gameDate;
            if (Objects.equals(openIndex, this.openIndex)) {
                Thread.sleep(1000L);
                this.gameInfo();
                return;
            }
            int gameSecond = resultData.getIntValue("gameSecond");
//            //这个地方需要根据老直播那边讨论好  这里暂时加15秒钟  接口返回是60秒钟
//            gameSecond=gameSecond+15;

            this.openIndex = openIndex;
            Date gameDateObj =sdf.parse(gameDate);
            Date nextDate = DateUtil.offsetSecond(gameDateObj, gameSecond - 10);

            JSONObject params = new JSONObject();
            params.put("openTime",DateUtil.offsetSecond(gameDateObj, gameSecond).getTime());

            redisService.setCacheObject("gameInfo:yhtx", params.toJSONString());
            TimerFactory.getTimer().schedule(new Yhtx1DataTask(2, this, 0), nextDate);
        } else {
            TimerFactory.getTimer().schedule(new Yhtx1DataTask(1, this), 1000L);
        }
    }


    public void luckMonster(int times) throws Exception {
        if (times > 25) {
            this.gameInfo();
        } else {
            boolean successStatus = false;
            JSONObject result = ykService.requestData(this.openIndex, this.gameCode,"银河探险-开奖结果");
            if (Objects.nonNull(result)) {
                JSONObject resultData = result.getJSONObject("data");
                if(Objects.nonNull(resultData)){
                    String number = resultData.getString("gameLottery");
                    // 测试随机
//                if(times == 20){
//                    number = String.valueOf(RandomUtil.randomInt(1,8));
//                }
                    if (StringUtils.isNotBlank(number) && !Objects.equals("x", number.toLowerCase())) {
                        JSONObject params = new JSONObject();
                        params.put("Number",Integer.parseInt(number)+1);


                        for (int i = 0; i <  DomainNameUtil.urls.length; i++) {
                            String url=DomainNameUtil.urls+"/yhtx/luckyMonster";
                            try {
                                ResponseEntity<String> responseEntity = RestTemplateUtils.post(url, params.toJSONString(), String.class);
                                String resp = responseEntity.getBody();
                                log.info(url + "-映客-银河探险-开奖结果同步请求响应：{}", resp);
                            } catch (RestClientException e) {
                                log.info(url + "-映客-银河探险-开奖结果同步请求异常：{}", e.getMessage());
                            }
                        }
                        TimerFactory.getTimer().schedule(new Yhtx1DataTask(1, this), 200);
                        successStatus = true;
                    }
                }

            }
            if (!successStatus) {
                times++;
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.info("异常 {}", e);
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
