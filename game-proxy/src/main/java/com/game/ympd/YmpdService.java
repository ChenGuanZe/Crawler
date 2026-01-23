package com.game.ympd;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.game.timer.TimerFactory;
import com.game.ympd.lczh.LczhService;
import com.game.ympd.yhtx.YhtxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class YmpdService {
    @Value("${ympd.domain}")
    private String ymzxDomain;
    @Value("${ympd.userName}")
    private String userName;
    @Value("${ympd.password}")
    private String password;
    @Autowired
    private LczhService lczhService;
    @Autowired
    private YhtxService yhtxService;
    private String token;

    public void login() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", userName);
        data.put("password", password);
        data.put("remember", 15);
        data.put("loading", 15);
        data.put("showPassword", 15);
        log.info("元梦之星登陆请求 请求参数 {}", JSONObject.toJSONString(data));
        HttpResponse execute = HttpUtil.createPost(ymzxDomain + "/user/account/Login")
                .form(data).execute();
        log.info("元梦之星登陆请求 返回参数 {}", execute.body());
        if (execute.isOk() && StringUtils.isNotBlank(execute.body())) {
            JSONObject result = JSONObject.parseObject(execute.body());
            int success = result.getIntValue("success");
            if (success == 1) {
                String token = result.getJSONObject("info").getString("token");
                this.token = token;
            } else {
                log.info("元梦之星登陆失败 请求参数 {}", JSONObject.toJSONString(data));
            }
        } else {

        }
    }

    public void restartValid() {
        TimerFactory.getTimer().schedule(() -> {
            try {
                log.info("线程池信息 {} {} {} {}", TimerFactory.getTimer().getPoolSize(), TimerFactory.getTimer().getActiveCount(), TimerFactory.getTimer().getTaskCount(), TimerFactory.getTimer().getCompletedTaskCount());
                long l = System.currentTimeMillis();
                l = l - 2 * 60 * 1000;
                if (lczhService.getGetTimeInfoTimes() < l) {
                    log.info("灵宠召唤重启");
                    lczhService.init();
                }
                if (yhtxService.getGetTimeInfoTimes() < l) {
                    log.info("银河探险重启");
                    yhtxService.init();
                }
            } catch (Exception e) {
                log.info("重启检测任务异常 ", e);
            }

        }, 10000, 10000);
    }

    public String getToken() {
        return token;
    }
}
