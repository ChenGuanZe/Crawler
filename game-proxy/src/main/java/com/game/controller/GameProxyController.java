package com.game.controller;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
//import com.game.bjx.BjxService;
import com.game.commom.RestTemplateUtils;
//import com.game.ljhd.util.SampleImg;
import com.game.redis.RedisService;
import com.game.uc.UuFarmService;
import com.game.utils.DomainNameUtil;
import com.game.utils.SafePointDrawWithFishingCN;
import com.game.utils.SafeWeeklyDrawWithWashSim;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏代理控制器
 *
 * @author 哈哈.唐
 * @date 2024-05-22 21:45
 */
@Slf4j
@RestController
@RequestMapping("/proxy")
public class GameProxyController {

     @Resource
     private UuFarmService uuFarmService;


    /**
     * 古城游戏代理
     *
     * @param url JSON
     * @return {@link String }
     */
    @PostMapping(value = "/gcGame")
    public String gcGame(String url) {
        log.info("古城请求参数：{}", url);
        ResponseEntity<String> responseEntity = RestTemplateUtils.get(url, String.class);
        String resp = responseEntity.getBody();
        log.info("古城请求响应：{}", resp);
        return resp;
    }

    /**
     * 幽林保卫战游戏代理
     *
     * @param url JSON
     * @return {@link String }
     */
    @PostMapping(value = "/ylbwzGame")
    public String ylbwzGame(String url, String Uuid, String Mid, String Pid) {
        log.info("幽林保卫战请求参数：{}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Uuid", Uuid);
        headers.set("Mid", Mid);
        headers.set("Pid", Pid);
        headers.set("Clienttime", System.currentTimeMillis() + "");
        headers.set("Dfid", "-");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> responseEntity = RestTemplateUtils.exchange(url, HttpMethod.GET,  entity, String.class);
        String resp = responseEntity.getBody();
        log.info("幽林保卫战请求响应：{}", resp);
        return resp;
    }
    @Resource
    private RedisService redisService;

    /**
     * 银河探险获取游戏信息
     *
     * @return {@link String }
     */
    @GetMapping(value = "/yhtxGameInfo")
    public String yhtxGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:yhtx");
        return cacheObject;
    }

    /**
     * 灵宠召唤获取游戏信息
     *
     * @return {@link String }
     */
    @GetMapping(value = "/lczhGameInfo")
    public String lczhGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:lczh");
        return cacheObject;
    }


    /**
     * 大话三国获取游戏信息
     *
     * @return {@link String }
     */
    @GetMapping(value = "/dhsgGameInfo")
    public String dhsgGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:dhsg");
        return cacheObject;
    }

    /**
     * 小红帽获取游戏信息
     *
     * @return {@link String }
     */
    @GetMapping(value = "/bjxxhmGameInfo")
    public String bjxxhmGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:bjxxhm");
        return cacheObject;
    }




    /**
     * 三国获取游戏信息
     *
     * @return {@link String }
     */
    @GetMapping(value = "/bjxsgGameInfo")
    public String bjxsgGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:bjxsg");
        return cacheObject;
    }

    /**
     * 怪物捕手获取游戏信息
     *
     * @return {@link String }
     */
    @GetMapping(value = "/bjxgwbsGameInfo")
    public String bjxgwbsGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:bjxgwbs");
        return cacheObject;
    }




    //重启游戏服务
//    @Resource
//    private BjxService bjxService;
//
//    @GetMapping(value = "/restartGameService")
//    public String restartGameService(int code,String account,String pwd) {
//        bjxService.restart(code,account,pwd);
//        return "";
//    }


    //连接互动
    @GetMapping(value = "/ljhdshmjGameInfo")
    public String ljhdshmjGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:ljhdshmj");
        return cacheObject;
    }

    //连接互动
    @GetMapping(value = "/ljhdyztxGameInfo")
    public String ljhdyztxGameInfo() {
        String cacheObject = redisService.getCacheObject("gameInfo:ljhdyztx");
        return cacheObject;
    }

    //uu农场保存游戏时间
    @GetMapping(value = "/addUCTime")
    public String addUCTime(int time) {
        log.info("uu农场上报的时间："+time);
        if (time<130&&time > 100) {
            Date date = new Date(); // 每期开始时间
            JSONObject params = new JSONObject();
            DateTime dateTime = DateUtil.offsetSecond(date, time);
            // 获取秒级时间戳（去掉毫秒）
            long seconds = dateTime.getTime() / 1000;
            params.put("openTime", (seconds*1000)-1000);
            try {
                // 设置 5 秒缓存时间
                redisService.setCacheObject( "gameInfo:ucGameTime", params.toString() );
            } catch (Exception e) {
                // 可以考虑加日志打印方便排查
                e.printStackTrace();
            }
        } else {
            if(time<=20){
                redisService.deleteObject("gameInfo:ucGameTime");
            }

        }
        return "OK";
    }


    //uu农场获取游戏信息
    @GetMapping(value = "/getUCTime")
    public String getUCTime() {
        String cacheObject = redisService.getCacheObject("gameInfo:ucGameTime");

        return cacheObject;
    }



    //uu农场获取游戏信息
    @GetMapping(value = "/uuFarmGameInfo")
    public String uuFarmGameInfo() {
        return uuFarmService.getGameResult(15);
    }




    //一千零一夜保存游戏时间
    @GetMapping(value = "/addYqlyyTime")
    public String addyqlyyTime(String time) {
        log.info("一千零一夜上报的时间："+time);
        if (time.length()>=10){
            JSONObject params = new JSONObject();
            params.put("openTime", (Long.parseLong(time)*1000)+1000);
            try {
                // 设置 5 秒缓存时间
                redisService.setCacheObject(
                        "gameInfo:yqlyyTime",
                        params.toString()

                );
            } catch (Exception e) {
                // 可以考虑加日志打印方便排查
                e.printStackTrace();
            }
        }else {
        long timenum=parseTime(time);
        if (timenum > 20) {
            Date date = new Date(); // 每期开始时间
            JSONObject params = new JSONObject();
            DateTime dateTime = DateUtil.offsetSecond(date, (int)timenum);
            // 获取秒级时间戳（去掉毫秒）
            long seconds = dateTime.getTime() / 1000;
            params.put("openTime", seconds*1000);
            try {
                // 设置 5 秒缓存时间
                redisService.setCacheObject(
                        "gameInfo:yqlyyTime",
                        params.toString(),
                        10l,
                        TimeUnit.SECONDS
                );
            } catch (Exception e) {
                // 可以考虑加日志打印方便排查
                e.printStackTrace();
            }
        } else {
         //   redisService.deleteObject("gameInfo:yqlyyTime");
        }
        }
        return "OK";
    }


    //uu农场获取游戏信息
    @GetMapping(value = "/getYqlyyTime")
    public String getYqlyyTime() {
        String cacheObject = redisService.getCacheObject("gameInfo:yqlyyTime");
        return cacheObject;
    }






    //宠物马拉松保存游戏时间
    @PostMapping("/addDwydh")
    public String addDwydh(@RequestBody String data) {
        if (data!=null){
            JSONObject jsonObject= new JSONObject(data);
            redisService.setCacheObject("gameInfo:GameTime:"+jsonObject.getStr("gameId"),jsonObject.toString());
        }
        return "OK";
    }

    //宠物马拉松获取游戏信息
    @GetMapping(value = "/getdwydhTime")
    public String getdwydhTime() {
        String cacheObject = redisService.getCacheObject("gameInfo:dwydhGameTime");
        return cacheObject;
    }

    public static int parseTime(String time) {
        if (time.contains(":")) {
            String[] parts = time.split(":");
            int seconds = 0;
            if (parts.length == 3) {
                seconds += Integer.parseInt(parts[0]) * 3600;
                seconds += Integer.parseInt(parts[1]) * 60;
                seconds += Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                seconds += Integer.parseInt(parts[0]) * 60;
                seconds += Integer.parseInt(parts[1]);
            }
            return seconds;
        } else {
            return Integer.parseInt(time);
        }
    }


    //保持游戏的时间
    @GetMapping(value = "setGameTime")
    public String setGameTime(long time,int gameId){
        if (time>1000&&gameId>0){
            JSONObject params = new JSONObject();
            if (String.valueOf(time).length() == 10) {
                time = time * 1000;
            }
            params.put("openTime", time);
            redisService.setCacheObject("gameInfo:GameTime:"+gameId, params.toString());
        }
        return  "ok";
    }


    //获取游戏时长
    @GetMapping(value = "getGameTime")
    public String getGameTime(int gameId){
        String cacheObject = redisService.getCacheObject("gameInfo:GameTime:"+gameId);
        log.info("游戏ID:{}- {}",gameId,cacheObject);
        return  cacheObject;
    }


    //删除游戏时长
    @GetMapping(value = "delGameTime")
    public String delGameTime(int gameId){
        redisService.deleteObject("gameInfo:GameTime:"+gameId);
        return  "ok";
    }


    private static final AtomicLong lastCallTime = new AtomicLong(0);


    //图片识别（深海秘境 ）
//    @GetMapping(value = "shmjGameImageRecog")
//    public  String shmjGameImageRecog(String imgUrl ,int gameId,String API_KEY,String SECRET_KEY ){
//
//        long now = System.currentTimeMillis();
//        long last = lastCallTime.get();
//
//        // 判断 5 秒内是否已经调用过
//        if (now - last < 5000) {
//            log.warn("接口限流：5 秒内只允许一次请求，本次被拒绝");
//            return "多次提交错误，5秒钟之内只接收一次请求";
//        }
//
//       String result = SampleImg.getImgStr(imgUrl, API_KEY, SECRET_KEY,0);
//        String fishName = "";
//        switch (result) {
//            case "1": fishName = "比目鱼"; break;
//            case "2": fishName = "小丑鱼"; break;
//            case "3": fishName = "鲸鱼"; break;
//            case "4": fishName = "鲨鱼"; break;
//            case "5": fishName = "大黄鱼"; break;
//            case "6": fishName = "大章鱼"; break;
//            case "7": fishName = "石斑鱼"; break;
//            case "8": fishName = "河豚"; break;
//        }
//        if (result==null||result.length()==0){
//            log.error("无法识别地址：{}",imgUrl);
//            return result;
//        }
//
//        Map<String, Object> params = new HashMap<>();
//        params.put("id", result);
//        params.put("name", fishName);
//        params.put("code", 1);
//        log.info("{} - 连接互动<深海迷境>- 开奖结果同步请求:{}",  JSONUtil.toJsonStr(params));
//        for (String url1 : DomainNameUtil.urls) {
//            url1+= "/ljhdshmj/luckyMonster";
//
//            try {
//                ResponseEntity<String> responseEntity = RestTemplateUtils.post(url1, JSONUtil.toJsonStr(params), String.class);
//                String resp = responseEntity.getBody();
//                log.info("{} - 连接互动<深海迷境> - 开奖结果同步请求响应：{}", url1, resp);
//            } catch (RestClientException e) {
//                log.warn("{} - 连接互动<深海迷境> - 开奖结果同步请求异常：{}", url1, e.getMessage());
//            } catch (Exception e) {
//                log.error("连接互动<深海迷境>-同步开奖结果异常", e);
//            }
//        }
//
//
//        log.info("识别地址: {} - ID: {} 名称: {}", imgUrl, result, fishName);
//     return  "ok";
//    }
//


    //自开测试
    @GetMapping(value = "selfOpening")
     public String  selfOpening(double [] bets  ,double[] multipliers){

        int result = SafePointDrawWithFishingCN.getInstance().getDrawResult(bets, multipliers);

        log.info("自开奖ID: {}",result);
        Map<String, Object> params = new HashMap<>();
        params.put("id", result);
        params.put("code", 0);

         return   JSONUtil.toJsonStr(params);
     }






}
