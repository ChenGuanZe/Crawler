package com.game.yk;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.Constants;
import com.game.timer.TimerFactory;
import com.game.yk.lczh.YkLczh1Service;
import com.game.yk.yhtx.YkYhtx1Service;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class YkService {
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Autowired
    private YkYhtx1Service ykYhtx1Service;
    @Autowired
    private YkLczh1Service ykLczh1Service;
    public static final String apiUrl = "http://148.66.10.10:8081/game/appGame/getLotteryInfo";
    public static final String classCode = "1007";

    public void init() {
        taskExecutor.execute(() -> {
            try {
                ykYhtx1Service.init();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        taskExecutor.execute(() -> {
            try {
                ykLczh1Service.init();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    public JSONObject requestData(String gameDate, Integer gameCode,String desc) {
        JSONObject result = null;
        Map<String, Object> map = new HashMap<>();
        map.put("gameCode", gameCode);
        map.put("classCode", YkService.classCode);
        if (StringUtils.isNotBlank(gameDate)) {
            map.put("gameDate", gameDate);
        }
        log.info("映客数据 {} 请求参数 {}", desc, JSONObject.toJSONString(map));
        String resultStr = HttpUtil.get(Constants.apiUrl1, map);
        log.info("映客数据 {} 返回参数 {}", desc, resultStr);
        if (StringUtils.isNotBlank(resultStr)) {
            try {
                result = JSONObject.parse(resultStr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

}
