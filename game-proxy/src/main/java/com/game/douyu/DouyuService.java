package com.game.douyu;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.commom.Constants;
import com.game.douyu.dahsg.DhsgService;
import com.game.timer.TimerFactory;
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
public class DouyuService {
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Autowired
    private DhsgService dhsgService;
    private final String classCode = "1006";

    public void init() {
        taskExecutor.execute(() -> {
            try {
                dhsgService.init();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    public JSONObject requestData(String gameDate, Integer gameCode, String desc) {
        JSONObject result = null;
        Map<String, Object> map = new HashMap<>();
        map.put("gameCode", gameCode);
        map.put("classCode", this.classCode);
        if (StringUtils.isNotBlank(gameDate)) {
            map.put("gameDate", gameDate);
        }
        log.info("斗鱼 {} 请求参数 {}", desc, JSONObject.toJSONString(map));
        String resultStr = HttpUtil.get(Constants.apiUrl1, map);
        log.info("斗鱼 {} 返回参数 {}", desc, resultStr);
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
