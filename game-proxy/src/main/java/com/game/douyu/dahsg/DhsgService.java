package com.game.douyu.dahsg;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.RestTemplateUtils;
import com.game.douyu.DouyuService;
import com.game.redis.RedisService;
import com.game.timer.TimerFactory;
import com.game.utils.DomainNameUtil;
import com.game.yk.YkService;
import com.game.yk.lczh.Lczh1DataTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

@Service
@Slf4j
public class DhsgService {
    private Integer gameCode = 9;
    private long getTimeInfoTimes = 0;
    private String openIndex = "期号"; // 期号

    public boolean enable = false;
    @Autowired
    private DouyuService douyuService;
    @Autowired
    private RedisService redisService;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat sdf1 = new SimpleDateFormat("MM-dd HH:mm");

    public void init() throws Exception {
        this.gameInfo();
        this.enable = true;
        log.info("大话三国 初始化成功");
    }

    public void gameInfo() throws Exception {
        this.getTimeInfoTimes = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        int i = calendar.get(Calendar.SECOND);
        if (i > 38) {
            calendar.add(Calendar.MINUTE, 1);
            calendar.set(Calendar.SECOND, 0);
        } else {
            calendar.set(Calendar.SECOND, 0);
        }

        Date time = calendar.getTime(); // 每期开始时间
        JSONObject params = new JSONObject();
        DateTime nextDate = DateUtil.offsetSecond(time, 44);
        params.put("openTime", DateUtil.offsetSecond(time, 60).getTime());
        redisService.setCacheObject("gameInfo:dhsg", params.toJSONString());

        {
            this.openIndex = sdf1.format(time);
        }

        TimerFactory.getTimer().schedule(new DhsgDataTask(2, this, 0), nextDate);

    }


    public void luckMonster(int times) throws Exception {
        if (times > 16) {
            this.gameInfo();
        } else {
            boolean successStatus = false;
            JSONObject result = douyuService.requestData(this.openIndex, this.gameCode, "大话三国-开奖结果");
            if (Objects.nonNull(result)) {

                JSONObject resultData = result.getJSONObject("data");
                if (Objects.nonNull(resultData)) {
                    String number = resultData.getString("gameLottery");
                    // 测试随机
//                if(times == 20){
//                    number = String.valueOf(RandomUtil.randomInt(1,8));
//                }
                    if (StringUtils.isNotBlank(number)) {
                        JSONObject params = new JSONObject();
                        int openNumber = Integer.parseInt(number) + 1;
                        params.put("Number", openNumber);
                        log.info("大话三国开奖号码 {}-{}", this.openIndex, openNumber);




                        for (int i = 0; i < DomainNameUtil.urls.length; i++) {
                            String url=DomainNameUtil.urls[i]+"/gameData/dhsg/luckyMonster2";
                            try {
                                ResponseEntity<String> responseEntity = RestTemplateUtils.post(url, params.toJSONString(), String.class);
                                String resp = responseEntity.getBody();
                                log.info(url + "-斗鱼-大话三国-开奖结果同步请求响应：{}", resp);
                            } catch (RestClientException e) {
                                log.info(url + "-斗鱼-大话三国-开奖结果同步请求异常：{}", e.getMessage());
                            }
                        }
                        TimerFactory.getTimer().schedule(new DhsgDataTask(1, this), 200);
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
