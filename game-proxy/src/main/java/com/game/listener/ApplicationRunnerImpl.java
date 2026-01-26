package com.game.listener;

import com.game.GameRestartService;
import com.game.douyu.dahsg.DhsgService;
import com.game.gcbwz.BwhdSocketClient;
import com.game.redis.RedisService;
import com.game.yk.YkService;
import com.game.ylbwz.YlbwzSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 应用程序启动时运行程序 impl
 *
 * @author 哈哈.唐
 * @date 2024-07-10 16:23
 */
@Slf4j
@Component
public class ApplicationRunnerImpl implements ApplicationRunner {
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;

    @Value("${manxiang.session.gcbwz}")
    private String gcSession;

    @Value("${manxiang.session.ylbwz}")
    private String ylSession;

    @Value("${manxiang.phone.ylbwz}")
    private String ylPhone;

    @Value("${manxiang.phone.ylbwz}")
    private String gcPhone;

    @Resource
    private RedisService redisService;
    @Resource
    YkService ykService;

//    @Resource
//    BjxService bjxService;
//    @Resource
//    LjhdService ljhdService;


    @Resource
    private GameRestartService gameRestartService;
    @Resource
    private DhsgService dhsgService;
    @Override
    public void run(ApplicationArguments args) throws Exception {






        //映客的获取数据暂时关闭  还有接口直接发送到游戏服务
       // ykService.init();
        // gameRestartService.restartValid();

//        dhsgService.init();
//
//        bjxService.init();

      //  ljhdService.init();  //启动的端口是  8086


//        ympdService.login();
//        taskExecutor.execute(() -> {
//            try {
//                yhtxService.init();
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        taskExecutor.execute(() -> {
//            try {
//                lczhService.init();
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        taskExecutor.execute(() -> {
//            ympdService.restartValid();
//        });


        // ---------------- 古城数据-屠龙 --------------------
    //    taskExecutor.execute(() -> {
    //        try {
    //            startGcSocket();
    //        } catch (InterruptedException e) {
    //            log.info("古城-socket发送消息报错1：{}", e.getMessage());
    //            try {
    //                startGcSocket();
    //            } catch (InterruptedException ex) {
    //                log.info("古城-socket发送消息报错2：{}", ex.getMessage());
    //            }
    //        }
    //    });

        //---------------- 幽林数据-幽林唱唱 --------------------
//        taskExecutor.execute(() -> {
//            try {
//                startYlbwzSocket();
//            } catch (InterruptedException e) {
//                log.info("幽林-socket发送消息报错1：{}", e.getMessage());
//                try {
//                    startYlbwzSocket();
//                } catch (InterruptedException ex) {
//                    log.info("幽林-socket发送消息报错2：{}", ex.getMessage());
//                }
//            }
//        });

//        taskExecutor.execute(() -> {
//            try {
//                startYlbwzSocket();
//            } catch (InterruptedException e) {
//                log.info("幽林-socket发送消息报错1：{}", e.getMessage());
//                try {
//                    startYlbwzSocket();
//                } catch (InterruptedException ex) {
//                    log.info("幽林-socket发送消息报错2：{}", ex.getMessage());
//                }
//            }
//        });

        log.info("启动执行完成。。。。。。。");
    }

    private void startGcSocket() throws InterruptedException {
        String webSocketUri = "ws://tulong.maxxiang.com/moriwss";
        BwhdSocketClient.start(webSocketUri, gcSession, gcPhone, redisService);

//        String webSocketUri = "ws://tulong.maxxiang.com/moriwss";
//        BwhdWebSocketClient client1 = BwhdWebSocketClient.getInstance(webSocketUri);
//        client1.sendMessage("{\"action\":1,\"session\":\"" + gcSession + "\"}");
//        while (true) {
//            client1.sendMessage("{\"action\":777,\"session\":\"" + gcSession + "\",\"phone\":\"" + gcPhone + "\"}");
//            Thread.sleep(5000);
//        }
    }

    private void startYlbwzSocket() throws InterruptedException {
        String webSocketUri = "ws://changchang.maxxiang.com/chang";
        YlbwzSocketClient.start(webSocketUri, ylSession, ylPhone, redisService);


//        String webSocketUri = "ws://changchang.maxxiang.com/chang";
//        YlbwzWebSocketClient client = YlbwzWebSocketClient.getInstance(webSocketUri);
//        client.sendMessage("{\"action\":1,\"session\":\"" + ylSession + "\"}");
//        while (true) {
//            client.sendMessage("{\"action\":777,\"session\":\"" + ylSession + "\",\"phone\":\"" + ylPhone + "\"}");
//            Thread.sleep(6000);
//        }
    }
}
