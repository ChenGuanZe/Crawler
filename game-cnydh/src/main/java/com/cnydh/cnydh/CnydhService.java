package com.cnydh.cnydh;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.utils.OkHttpUtil;
import com.utils.TimerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.utils.DomainNameUtil;
import org.springframework.web.client.RestClientException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CnydhService {

    public final static String[] urls = new String[]{
          //  "http://8888888/app-api/qmgame/cnydh",

    };


    @Autowired
    private CnydhService douyuService;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat sdf1 = new SimpleDateFormat("MM-dd HH:mm");

    public void init() throws Exception {
        this.gameInfo();

        log.info("超能运动会 初始化成功");
    }

    public void gameInfo() {

        try {

            log.info("超能运动会<YO直播>获取游戏信息请求--地址 " + DomainNameUtil.cnydh_room_game_url);
            String res = OkHttpUtil.get(DomainNameUtil.cnydh_room_game_url,null);
            log.info("超能运动会<YO直播>获取游戏信息请求--结果 " + res);
            JSONObject jsonObject = JSONUtil.parseObj(res);
            JSONObject stageInfo = jsonObject.getJSONArray("stage_list").getJSONObject(0);
            if (stageInfo.getInt("play_status")==1){
                Date time = new Date();
                DateTime nextDate = DateUtil.offsetSecond(time, stageInfo.getInt("remain_duration"));

                for (String url : DomainNameUtil.transitUrls) {
                    try {
                        url+="/gameProxy/proxy/setGameTime?gameId=26&time="+ nextDate.getTime();
                        String resp =OkHttpUtil.get(url,null);
                        log.info("{} - 超能运动会<YO直播>  - 发送游戏开始时间：{}", url, nextDate.getTime());
                    } catch (RestClientException e) {
                        log.warn("{} - 超能运动会<YO直播>  - 发送游戏开始时间异常：{}", url, e.getMessage());
                    } catch (Exception e) {
                        log.error("超能运动会<YO直播>  发送游戏开始时间异常", e);
                    }
                }



                TimerFactory.getTimer().schedule(new CnydhDataTask(2, this), nextDate);
                return;
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        try {
           Thread.sleep(1000);
           gameInfo();
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    public void luckMonster() throws Exception {
        for (String url : DomainNameUtil.transitUrls) {
            try {
                url+="/gameProxy/proxy/delGameTime?gameId=26";
                String resp =OkHttpUtil.get(url,null);
                log.info("{} - 超能运动会<YO直播> - 删除游戏开始时间：{}", url, resp);
            } catch (RestClientException e) {
                log.warn("{} - 超能运动会<YO直播>- 删除游戏开始时间异常：{}", url, e.getMessage());
            } catch (Exception e) {
                log.error("超能运动会<YO直播>  删除游戏开始时间异常", e);
            }
        }


        try {
            for (int i=0;i<=5;i++){
                log.info("超能运动会<YO直播>获取游戏信息请求--地址 " + DomainNameUtil.cnydh_room_game_url);
                String res = OkHttpUtil.get(DomainNameUtil.cnydh_room_game_url,null);
                log.info("超能运动会<YO直播>获取游戏信息请求--结果 " + res);
                JSONObject jsonObject = JSONUtil.parseObj(res);
                int play_status = jsonObject.getInt("play_status");
                if (play_status == 3) {
                   int number = jsonObject.getJSONArray("animal_list").getInt(0);

                    Map<String, Object> params = new HashMap<>();
                    params.put("monsterId", number);
                    params.put("code", 1);
                    log.info("超能运动会<YO直播>- 开奖结果同步请求:{}",  JSONUtil.toJsonStr(params));
                    for (String url :urls) {
                        try {
                            url+="/luckyMonster";
                            String resp = OkHttpUtil.postJson(url,JSONUtil.toJsonStr(params));
                            log.info("{} - 超能运动会<YO直播> - 开奖结果同步请求响应：{}", url, resp);
                        } catch (RestClientException e) {
                            log.warn("{} - 超能运动会<YO直播> - 开奖结果同步请求异常：{}", url, e.getMessage());
                        } catch (Exception e) {
                            log.error("超能运动会<YO直播>-同步开奖结果异常", e);
                        }
                    }


                  break;

                }

                Thread.sleep(500);
            }


        } catch (Exception e) {
            log.info("超能运动会<YO直播>获取游戏信息异常：" + e.getMessage());
        }
        DateTime nextDate = DateUtil.offsetSecond(new Date(), 2);
        TimerFactory.getTimer().schedule(new CnydhDataTask(1, this), nextDate);

    }

}
