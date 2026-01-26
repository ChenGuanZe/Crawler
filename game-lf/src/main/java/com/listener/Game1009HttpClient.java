package com.listener;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.utils.DomainNameUtil;
import com.utils.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//海岛探险1009
@Component
@Slf4j
public class Game1009HttpClient {

    public final static String[] urls = new String[]{
            "http://134.122.128.242/wanshunGame",
            "http://156.251.17.107/wanshunGame",
    };


    private String lastGameOpenId = "";
    private long lastOpenTime = 0;
//    private String game_remote_url = "";
    private static String game_remote_token = "UM_distinctid=19aed4bdc78910-0235859598053d-27604a30-3d10d-19aed4bdc79d45; cna=0413cc6a692847f8935af117f5adf55e; xlly_s=1; L_pck_rm=icdcZZjT39HDn0dL70ytwO%2FRCjMx7DQ4smUuQ3MPdxsEuWzBTIxZFu86%2F%2FzDFUYiOgGaU4vTbC1b%2FxN2XnN3qRpFng4aKLOlghNtUV6duOCTQ1l9ct6zw6sJn2eH2pTkXflDTYvy3wjU0ixz4Tv0Pg%3D%3D; fansTuan-tips=vistived; laifeng_react_page_xingzuoV2_firstShow=2025-12-05; __ysuid=1764938700523Ldx; tfstk=gjpsUrfsIP46A4RR1toUN3u08PXXcDkrfosvqneaDOBtHSQBJcxZmcAblwTc71W93JLBKe243cfqH6Ic0-LXmcjAkELfuClEUhxGnt3yGYkyj1URXCOf6sEKDGjjYGHgggEGnt3r8ICX8jWD5iehxsKKAiS4BlLAkkCd-isAkEQO9yI5qtQvkECdpiITXlCOD9ed-iBAXEBxA9QhctQvktnBvb38ViG1Shil841aGoY2XwwYHdHc6aw1JMjBdE11y6QQH_JCf1_JXLE2DKSpLp1PTouFp3AeWM6SL5BJAILWwEcztOtv6EfBS2VC8BdeDZCL4-KCGNOpB6ZYHHXOlsdX5VNGWC8CZMCLcYjezwKMBBi0JhLP5OIdTYnWvsdeI_vZJR6JZhWwMEcztOtv69srO875SePbA_2AAaoIASV0lTdSuTc2YAfOxG1EADa9i1IhAaoIASVc6Mj14DiQWIf..; isg=BJiYPamSRUndAGnNAHYkYLDaacYqgfwLp964C9KJVVOGbTpXe5FMm_8DpaXd_bTj";


    //填入token
    private final long gamePeriod = TimeUnit.SECONDS.toMillis(55);

    private final String game_remote_url="https://yapi.laifeng.com/lftop/mtop.youku.laifeng.Interstellar.scene.info/2.0/";

    public synchronized void trySync() {

        if (System.currentTimeMillis() - lastOpenTime > gamePeriod) {
            try {

                if(game_remote_token==null || "".equals(game_remote_token)){
                     log.info("请填入token");
                }
                HttpRequest request = HttpUtil.createPost(game_remote_url);
                request.header("authority", "yapi.laifeng.com");
                request.header("method", "POST");
                request.header("path", "/lftop/mtop.youku.laifeng.Interstellar.scene.info/2.0/");
                request.header("scheme", "https");
                request.header("accept", "*/*");
                request.header("accept-encoding", "gzip, deflate, br, zstd");
                request.header("accept-language", "zh-CN,zh;q=0.9");
                request.header("content-length", "189");
                request.header("content-type", "application/x-www-form-urlencoded");
                request.header("cookie", game_remote_token);
                request.header("origin", "https://yv.laifeng.com");
                request.header("priority", "u=1, i");
                request.header("referer", "https://yv.laifeng.com/");
                request.header("sec-ch-ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
                request.header("sec-ch-ua-mobile", "?0");
                request.header("sec-ch-ua-platform", "\"Android\"");
                request.header("sec-fetch-dest", "empty");
                request.header("sec-fetch-mode", "cors");
                request.header("sec-fetch-site", "same-site");
                request.header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");

                HashMap<String, Object> paramMap = new HashMap<>();
                paramMap.put("ename", "tandaoxunbao1");
                paramMap.put("appKey", "24679788");
                paramMap.put("info", "{\"clientInfo\":{\"appName\":\"laifengPc\",\"appVersion\":\"1.0.0\",\"appKey\":\"24679788\",\"appId\":1000}}");
                request.form(paramMap);
                request.timeout(1500);
                String body = request.execute().body();
                log.info("远程获取游戏[{}] 原始开奖结果： => {}", game_remote_url, body);
                JSONObject res = JSONUtil.parseObj(body);
                if ("SUCCESS::调用成功".equals(res.getJSONArray("ret").getStr(0))) {
                    JSONObject data = res.getJSONObject("data");
                    String openId = data.getInt("id")+"";
                    log.info("远程获取游戏 lastGameOpenId is {},openId is {} 原始开奖结果：{} ",lastGameOpenId, openId, data.getInt("betStatus"));
                    if (!lastGameOpenId.equals(openId) && 3==data.getInt("betStatus")) {
                        Integer monsterId = data.getInt("rewardId");
                        String monsterName = data.getStr("rewardName");
                        if(monsterId==698){//凤舞
                            monsterId=8;
                        }else if(monsterId==699){//黑石
                            monsterId=7;
                        }else if(monsterId==700){//蓝海
                            monsterId=2;
                        }else if(monsterId==701){//龙鳞
                            monsterId=1;
                        }else if(monsterId==702){//绿洲岛
                            monsterId=6;
                        }else if(monsterId==703){//梦境岛
                            monsterId=5;
                        }else if(monsterId==704){//银月岛
                            monsterId=4;
                        }else if(monsterId==705){//紫烟岛
                            monsterId=3;
                        }

                        log.info("远程获取游戏[{}]开奖结果,: name :{}", monsterId,monsterName);
                        Map<String, Object> params = new HashMap<>();
                        params.put("monsterId",monsterId);
                        params.put("code", 1);
                          //发送结果
                        for (String url :urls) {
                            try {
                                url+="/tdxb/luckyMonster";
                                String resp = OkHttpUtil.postJson(url,JSONUtil.toJsonStr(params));
                                log.info("{} - 探岛寻宝<来疯直播>  - 开奖结果同步请求响应：{}", url, resp);
                            } catch (RestClientException e) {
                                log.warn("{} - 探岛寻宝<来疯直播>  - 开奖结果同步请求异常：{}", url, e.getMessage());
                            } catch (Exception e) {
                                log.error("探岛寻宝<来疯直播> -同步开奖结果异常", e);
                            }
                        }





                        Date time = new Date(); // 每期开始时间
                        DateTime dateTime=DateUtil.offsetSecond(time, 55);
                        for (String url : DomainNameUtil.transitUrls) {
                            try {
                                url+="/gameProxy/proxy/setGameTime?gameId=29&time="+dateTime.getTime();
                                String resp = OkHttpUtil.get(url,null);
                                log.info("{} - 探岛寻宝<来疯直播>  - 发送游戏开始时间：{}", url, resp);
                            } catch (RestClientException e) {
                                log.warn("{} - 探岛寻宝<来疯直播>  - 发送游戏开始时间异常：{}", url, e.getMessage());
                            } catch (Exception e) {
                                log.error("探岛寻宝<来疯直播>  发送游戏开始时间异常", e);
                            }
                        }







                        lastGameOpenId = openId;

                        lastOpenTime = new Date().getTime();


                    }

                }
            } catch (Exception e) {

            }
        }


    }




    public static void main(String[] args) {
        Game1009HttpClient client = new Game1009HttpClient();
        while (true) {
            client.trySync();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

}
