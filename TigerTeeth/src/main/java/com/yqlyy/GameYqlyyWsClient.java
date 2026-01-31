package com.yqlyy;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.commom.RestTemplateUtils;
import com.entity.AccountedNotify.OpenTreasureHunter;
import com.entity.AccountedNotify.TreasureHunterInfoItem;
import com.entity.BussesCmd;
import com.entity.GameStartData;
import com.entity.WsCmd;
import com.qq.tars.protocol.tars.TarsInputStream;
import com.utils.DomainNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import javax.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

@ClientEndpoint
public class GameYqlyyWsClient {

    public  RestTemplateUtils restTemplateUtils;

    private static final Logger logger = LoggerFactory.getLogger(GameYqlyyWsClient.class);
    String wsUrl = "wss://3b258d37-ws.va.huya.com/?baseinfo=DBYgMGE3ZDUwNmYwNjRjMDA2OTY0MDIxN2E3MThhNzA3OWMmGndlYmg1JjI1MTAzMDEwNDkmd2Vic29ja2V0NgxIVVlBJlpIJjIwNTJGAFYvMTY1ODIuMjUzMzYsNDQyOTkuNzY5NDksNDYzOTIuODIxMDYsNDkwMjMuODgwMTBsdgCGAJYAqAACBghIVVlBX05FVBYBMAYLSFVZQV9WU0RLVUEWGndlYmg1JjI1MTAzMDEwNDkmd2Vic29ja2V0";
    //String wsUrl="wss://0e776a5d-ws.va.huya.com/?baseinfo=DBYgMGE3ZDUwNmYwNjRjMDA2OTY0MDIxN2E3MThhNzA3OWMmGndlYmg1JjI1MTAzMDEwNDkmd2Vic29ja2V0NgxIVVlBJlpIJjIwNTJGAFYvMTY1ODIuMjUzMzYsNDQyOTkuNzY5NDksNDYzOTIuODIxMDYsNDkwMjMuODgwMTBsdgCGAJYAqAACBghIVVlBX05FVBYBMAYLSFVZQV9WU0RLVUEWGndlYmg1JjI1MTAzMDEwNDkmd2Vic29ja2V0";
    private int messageId = 1;
    private Session session;

    public GameYqlyyWsClient(RestTemplateUtils restTemplateUtils){
        this.restTemplateUtils=restTemplateUtils;


    }

    @OnMessage
    public void pongMessage(Session session, PongMessage msg) {
        logger.info("[一千零一夜<虎牙>]收到的PongMessage消息: {}", msg);
    }

    @OnMessage
    public void binaryMessage(Session session, ByteBuffer msg) {
        // logger.info("[一千零一夜<虎牙>]收到的binaryMessage消息: {}", msg);

        byte[] byteArray = msg.array();
        // logger.info("一千零一夜<虎牙>]收到的binaryMessage消息: {}", Base64.getEncoder().encodeToString(byteArray));
//        for (byte b : byteArray) {
//            System.out.print(b + " ");
//        }

        TarsInputStream inputStream = new TarsInputStream(msg.array());
        WsCmd cmd = new WsCmd();
        cmd.setiCmdType(inputStream.read(cmd.getiCmdType(), 0, false));
        //  logger.info("[一千零一夜<虎牙>]收到的binaryMessage消息指令: {}", cmd.getiCmdType());
        if (cmd.getiCmdType() != 7) {
            return;
        }
        byte[] vData = inputStream.read(cmd.getvData(), 1, false);
        inputStream = new TarsInputStream(vData);
        BussesCmd bussesCmd = new BussesCmd();
        //  System.out.println("一千零一夜<虎牙>ePushType is:" + inputStream.read(bussesCmd.ePushType, 0, false));
        bussesCmd.iUri = inputStream.read(bussesCmd.iUri, 1, false);
        //   System.out.println("一千零一夜<虎牙>iUri is:" + bussesCmd.iUri);
        bussesCmd.sMsg = inputStream.read(bussesCmd.sMsg, 2, false);
        // System.out.println("一千零一夜<虎牙>sMsg is:" + bussesCmd.sMsg.length);
        if (bussesCmd.iUri == 7109) {
            System.out.println(">>>>>>>7109>>>>>>>>");
            OpenTreasureHunter openTreasureHunter = new OpenTreasureHunter();
            inputStream = new TarsInputStream(bussesCmd.sMsg);
            openTreasureHunter.readFrom(inputStream);
//            System.out.println(inputStream.read(openTreasureHunter.getlOldRoundId(), 0, false));
//            bussesCmd.sMsg=inputStream.read(bussesCmd.sMsg,2,false);
//            System.out.println(inputStream.read(openTreasureHunter.getvTreasure(), 4, false));
            if(openTreasureHunter.getvTreasure()!=null && openTreasureHunter.getvTreasure().size()>0){




                JSONArray jsonArray=new JSONArray();
                for(int i=0;i<openTreasureHunter.getvTreasure().size();i++) {
                    int iTreasureId=((TreasureHunterInfoItem) openTreasureHunter.getvTreasure().get(i)).getiTreasureId();
                    String sTreasureName=((TreasureHunterInfoItem) openTreasureHunter.getvTreasure().get(i)).getsTreasureName();
                    System.out.println("一千零一夜<虎牙>开奖动物name：" + sTreasureName);
                    System.out.println("一千零一夜<虎牙>夜开奖动物id：" + iTreasureId);
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.set("monsterId",iTreasureId);
                    jsonObject.set("monsterName",sTreasureName);
                    jsonArray.add(jsonObject);
                }
                if (!jsonArray.isEmpty()){
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.set("data",jsonArray);
                    for (String url : DomainNameUtil.urls) {
                        try {
                            url+="/yqlyy/luckyMonster";
                            ResponseEntity<String> responseEntity = restTemplateUtils.post(url, jsonObject.toString(), String.class);
                            String resp = responseEntity.getBody();
                            logger.info("{} - 虎牙-一千零一夜 - 开奖结果同步请求响应：{}", url, resp);
                        } catch (RestClientException e) {
                            logger.warn("{} - 虎牙-一千零一夜 - 开奖结果同步请求异常：{}", url, e.getMessage());
                        } catch (Exception e) {
                            logger.error("虎牙-一千零一夜-同步开奖结果异常", e);
                        }
                    }

                }


            }
        } else if (bussesCmd.iUri == 7107) {
            System.out.println(">>>>>>>7107>>>>>>>>");
            inputStream = new TarsInputStream(bussesCmd.sMsg);
            GameStartData gameStartData = new GameStartData();
            Long OldRoundIndexEndTime= inputStream.read(gameStartData.getlOldRoundIndexEndTime(), 5, false);
            Long OldRoundIndexTime=inputStream.read(gameStartData.getlOldRoundIndexTime(), 4, false);


            System.out.println(inputStream.read(gameStartData.getlOldRoundId(), 0, false));

            System.out.println("一千零一夜<虎牙>游戏gameStartData.getlOldRoundIndexTime()：" +OldRoundIndexTime);
            System.out.println("一千零一夜<虎牙>游戏gameStartData.getlOldRoundIndexEndTime()：" + OldRoundIndexEndTime);
            System.out.println(inputStream.read(gameStartData.getlOldRoundId(), 3, false));



            for (String url : DomainNameUtil.transitUrls) {
                try {
                    url+="/gameProxy/proxy/setGameTime?time="+OldRoundIndexEndTime+"&gameId=25";
                    ResponseEntity<String> responseEntity = restTemplateUtils.get(url, String.class);
                    String resp = responseEntity.getBody();
                    logger.info("{} - 虎牙-一千零一夜 - 同步请求时间响应：{}", url, resp);
                } catch (RestClientException e) {
                    logger.warn("{} - 虎牙-一千零一夜 - 同步请求时间响应异常：{}", url, e.getMessage());
                } catch (Exception e) {
                    logger.error("虎牙-一千零一夜-同步开奖结果异常", e);
                }
            }


        }else if (bussesCmd.iUri == 7103) {
            System.out.println(">>>>>>>7103>>>>>>>>");
            OpenTreasureHunter openTreasureHunter = new OpenTreasureHunter();
            inputStream = new TarsInputStream(bussesCmd.sMsg);
            openTreasureHunter.readFrom(inputStream);
//            System.out.println(inputStream.read(openTreasureHunter.getlOldRoundId(), 0, false));
//            bussesCmd.sMsg=inputStream.read(bussesCmd.sMsg,2,false);
//            System.out.println(inputStream.read(openTreasureHunter.getvTreasure(), 4, false));
            if(openTreasureHunter.getvTreasure()!=null && !openTreasureHunter.getvTreasure().isEmpty()){

                int iTreasureId=((TreasureHunterInfoItem)openTreasureHunter.getvTreasure().get(0)).getiTreasureId();
                String sTreasureName=((TreasureHunterInfoItem)openTreasureHunter.getvTreasure().get(0)).getsTreasureName();
                System.out.println("[宠物马拉松<虎牙>]游戏开局动物name："+sTreasureName);
                System.out.println("[宠物马拉松<虎牙>]游戏开局动物id："+iTreasureId);

                JSONObject jsonObject=new JSONObject();
                jsonObject.set("monsterId",iTreasureId);
                jsonObject.set("sTreasureName",sTreasureName);


                for (String url : DomainNameUtil.urls) {
                    try {
                        url+="/mls/luckyMonster";
                        ResponseEntity<String> responseEntity = restTemplateUtils.post(url, jsonObject.toString(), String.class);
                        String resp = responseEntity.getBody();
                        logger.info("{} - 虎牙-宠物马拉松 - 开奖结果同步请求响应：{}", url, resp);
                    } catch (RestClientException e) {
                        logger.warn("{} - 虎牙-宠物马拉松 - 开奖结果同步请求异常：{}", url, e.getMessage());
                    } catch (Exception e) {
                        logger.error("虎牙-宠物马拉松-同步开奖结果异常", e);
                    }
                }

            }


        } else if (bussesCmd.iUri == 7101) {
            System.out.println(">>>>>>>7101>>>>>>>>");
            inputStream = new TarsInputStream(bussesCmd.sMsg);
            GameStartData gameStartData = new GameStartData();
            Long OldRoundIndexEndTime= inputStream.read(gameStartData.getlOldRoundIndexEndTime(), 5, false);
            Long OldRoundIndexTime=inputStream.read(gameStartData.getlOldRoundIndexTime(), 4, false);

            System.out.println(inputStream.read(gameStartData.getlOldRoundId(), 0, false));
            System.out.println("[宠物马拉松<虎牙>]游戏gameStartData.getlOldRoundIndexTime()：" +OldRoundIndexTime );
            System.out.println("[宠物马拉松<虎牙>]游戏gameStartData.getlOldRoundIndexEndTime()：" +OldRoundIndexEndTime);
            System.out.println(inputStream.read(gameStartData.getlOldRoundId(), 3, false));
            //获取游戏开奖赔率开始
            OpenTreasureHunter openTreasureHunter=new OpenTreasureHunter();
            List<TreasureHunterInfoItem> list =(List<TreasureHunterInfoItem>)inputStream.read(openTreasureHunter.getvTreasure(),8,false);
            //System.out.println(inputStream.read(treasureHunterInfo.getValue(),8,false));

            // 构建倍率数据JSON
            JSONArray rateArray = new JSONArray();
            for(int i=1;i<list.size();i++){
                TreasureHunterInfoItem item=list.get(i);

                int rawRate = item.getiRate();
                int mappedRate = mapRateOnlyForMarathon(22, rawRate);

                System.out.println("游戏开奖倍率(raw->mapped)-----" + rawRate + "->" + mappedRate + " name is :" + item.sTreasureName + " id is :" + item.iTreasureId);

                JSONObject rateItem = new JSONObject();
                rateItem.set("monsterId", item.iTreasureId);
                rateItem.set("monsterName", item.sTreasureName);
                rateItem.set("rawRate", rawRate);
                rateItem.set("rate", mappedRate);
                rateArray.add(rateItem);
            }

            // 直接推送倍率数据到第三方业务系统
            if (!rateArray.isEmpty()) {
                JSONObject rateData = new JSONObject();
                rateData.set("gameId", 22);
                rateData.set("gameName", "宠物马拉松");
                rateData.set("rateList", rateArray);

                for (String url : DomainNameUtil.urls) {
                    try {
                        String rateUrl = url + "/mls/gameRate";
                        ResponseEntity<String> responseEntity = restTemplateUtils.post(rateUrl, rateData.toString(), String.class);
                        String resp = responseEntity.getBody();
                        logger.info("{} - 虎牙-宠物马拉松 - 推送倍率数据响应：{}", rateUrl, resp);
                    } catch (RestClientException e) {
                        logger.warn("{} - 虎牙-宠物马拉松 - 推送倍率数据异常：{}", url, e.getMessage());
                    } catch (Exception e) {
                        logger.error("虎牙-宠物马拉松-推送倍率数据异常", e);
                    }
                }
            }
            //获取游戏开奖赔率结束


            //发送中转爬虫服务器
            for (String url : DomainNameUtil.transitUrls) {
                try {
                    url+="/gameProxy/proxy/setGameTime?time="+OldRoundIndexEndTime+"&gameId=22";
                    ResponseEntity<String> responseEntity = restTemplateUtils.get(url, String.class);
                    String resp = responseEntity.getBody();
                    logger.info("{} - 虎牙-宠物马拉松 - 同步请求时间响应：{}", url, resp);
                } catch (RestClientException e) {
                    logger.warn("{} - 虎牙-宠物马拉松 - 同步请求时间响应异常：{}", url, e.getMessage());
                } catch (Exception e) {
                    logger.error("虎牙-宠物马拉松-同步开奖结果异常", e);
                }
            }




        }
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.info("[一千零一夜<虎牙>]onOpen");
        byte[] temp = {
                0, 3, 29, 0, 1, 1, 54, 0, 0, 1, 54, 16, 3, 44, 60, 64, 17, 86, 8, 104, 121, 112, 99, 100, 110, 103, 119, 102, 30, 111, 110, 67, 108, 105, 101, 110, 116, 71, 101, 116, 83, 116, 117, 110, 65, 110, 100, 80, 99, 100, 110, 80, 114, 111, 120, 121, 82, 101, 113, 125, 0, 1, 0, -8, 8, 0, 1, 6, 4, 116, 82, 101, 113, 29, 0, 1, 0, -22, 10, 2, 0, 2, 54, -41, 19, 0, 0, 1, 23, 80, 112, 106, -86, 38, 73, 49, 54, 55, 48, 51, 49, 51, 55, 54, 50, 45, 49, 54, 55, 48, 51, 49, 51, 55, 54, 50, 45, 55, 49, 55, 51, 57, 52, 50, 57, 56, 49, 56, 52, 56, 55, 50, 55, 53, 53, 50, 45, 51, 51, 52, 48, 55, 53, 48, 57, 56, 48, 45, 49, 48, 48, 53, 55, 45, 65, 45, 48, 45, 49, 95, 52, 52, 48, 95, 50, 95, 54, 54, 48, 8, 73, 0, 5, 0, 1, 0, 3, 0, 4, 0, 5, 0, 7, 86, 32, 48, 97, 55, 100, 53, 48, 54, 102, 48, 54, 52, 99, 48, 48, 54, 57, 54, 52, 48, 50, 49, 55, 97, 55, 49, 56, 97, 55, 48, 55, 57, 99, 98, 99, -114, -9, 34, 112, 14, -128, 4, -109, 0, 0, 0, 0, 0, 0, 0, 0, -96, 1, -74, 0, -52, -36, -26, 0, -4, 15, -10, 16, 0, -16, 17, 3, -16, 18, 1, -8, 19, 0, 5, 0, 1, 16, 1, 0, 3, 16, 1, 0, 4, 16, 1, 0, 5, 16, 1, 0, 7, 16, 1, -4, 20, -4, 21, -8, 22, 0, 1, 6, 20, 95, 119, 101, 98, 65, 108, 108, 111, 119, 67, 114, 111, 115, 115, 87, 101, 98, 114, 116, 99, 22, 1, 49, 11, -116, -104, 12, -88, 12, 44, 54, 37, 54, 99, 100, 56, 50, 50, 50, 57, 48, 98, 49, 55, 102, 48, 99, 51, 58, 54, 99, 100, 56, 50, 50, 50, 57, 48, 98, 49, 55, 102, 48, 99, 51, 58, 48, 58, 48, 76, 92, 102, 32, 56, 48, 52, 49, 101, 51, 102, 49, 55, 97, 100, 99, 101, 101, 53, 100, 98, 56, 51, 55, 57, 97, 57, 53, 53, 54, 53, 97, 101, 49, 56, 102, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        String str = "ABAdAAAnCQACBg9saXZlOjE2NzAzMTM3NjIGD2NoYXQ6MTY3MDMxMzc2MhYAIAE2AExcZgA=";
        temp = Base64.getDecoder().decode(str);
        logger.info("[<虎牙>] msg is:消息: {}", Base64.getEncoder().encodeToString(temp));
        ByteBuffer buffer = ByteBuffer.wrap(temp);
        try {
            session.getBasicRemote().sendBinary(buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    @OnClose
    public void onClose() {
        logger.error("[<虎牙>]链接关闭!Close to server");
    }

    @OnError
    public void onError(Throwable e, Session session) {
        logger.error("[<虎牙>]监听到异常", e);
    }

    private synchronized void connect() {
        try {
            logger.info("一千零一夜<虎牙>准备链接GameYDHClient服务...");
            WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();
            webSocketContainer.setDefaultMaxTextMessageBufferSize(65536);
            webSocketContainer.setDefaultMaxBinaryMessageBufferSize(65536);
            webSocketContainer.setDefaultMaxSessionIdleTimeout(30000); // 10秒
            webSocketContainer.setAsyncSendTimeout(20000);


            if (wsUrl == null || "".equals(wsUrl)) {
                logger.info("<虎牙>远程获取游戏url[{}] ", wsUrl);
                return;
            }

            String wsUrlStr = wsUrl;
            logger.info("<虎牙>准备链接GameYDHClient服务 => {}", wsUrlStr);
            URI uri = new URI(wsUrlStr);
            session = webSocketContainer.connectToServer(this, uri);
        } catch (Exception e) {
            logger.error("<虎牙>[{}]链接失败", wsUrl, e);
        }
    }

    public synchronized void report() {
        if (session == null || !session.isOpen()) {
            logger.info("[<虎牙>]已关闭，进行重新链接");
            connect();
        }
        String str="ABAdAAEGvQkAUAYOaHlleHQ6cmk0dHV0NmgGGWh5ZXh0OnJpNHR1dDZoXzE2NzAzMTM3NjIGDmh5ZXh0Omxpc29udTMyBhloeWV4dDpsaXNvbnUzMl8xNjcwMzEzNzYyBg5oeWV4dDoyaTAzZXlveAYZaHlleHQ6MmkwM2V5b3hfMTY3MDMxMzc2MgYOaHlleHQ6eDkya2RwZWQGGWh5ZXh0Ong5MmtkcGVkXzE2NzAzMTM3NjIGDmh5ZXh0OmFhNTlqOThwBhloeWV4dDphYTU5ajk4cF8xNjcwMzEzNzYyBg5oeWV4dDpwNWdwaDA5NgYZaHlleHQ6cDVncGgwOTZfMTY3MDMxMzc2MgYOaHlleHQ6czYzcm03MHIGGWh5ZXh0OnM2M3JtNzByXzE2NzAzMTM3NjIGDmh5ZXh0OjdxNWR0YTUyBhloeWV4dDo3cTVkdGE1Ml8xNjcwMzEzNzYyBg5oeWV4dDo5ZnNqYXZlbwYZaHlleHQ6OWZzamF2ZW9fMTY3MDMxMzc2MgYOaHlleHQ6MWo5NXA3YjQGGWh5ZXh0OjFqOTVwN2I0XzE2NzAzMTM3NjIGDmh5ZXh0Om9meGlhZ2o1BhloeWV4dDpvZnhpYWdqNV8xNjcwMzEzNzYyBg5oeWV4dDptZDV6MmFucQYZaHlleHQ6bWQ1ejJhbnFfMTY3MDMxMzc2MgYOaHlleHQ6cTFnOGMzcWkGGWh5ZXh0OnExZzhjM3FpXzE2NzAzMTM3NjIGDmh5ZXh0OnRudWF1cjk3BhloeWV4dDp0bnVhdXI5N18xNjcwMzEzNzYyBg5oeWV4dDpsbHV6YjBrcQYZaHlleHQ6bGx1emIwa3FfMTY3MDMxMzc2MgYOaHlleHQ6bTg3NWl3c2cGGWh5ZXh0Om04NzVpd3NnXzE2NzAzMTM3NjIGDmh5ZXh0OjNtenJsZnR2BhloeWV4dDozbXpybGZ0dl8xNjcwMzEzNzYyBg5oeWV4dDpudm50YmU5dAYZaHlleHQ6bnZudGJlOXRfMTY3MDMxMzc2MgYOaHlleHQ6cGl6YnBjMW4GGWh5ZXh0OnBpemJwYzFuXzE2NzAzMTM3NjIGDmh5ZXh0Omg4c2NkN3Q5BhloeWV4dDpoOHNjZDd0OV8xNjcwMzEzNzYyBg5oeWV4dDprbnZsZWNvcAYZaHlleHQ6a252bGVjb3BfMTY3MDMxMzc2MgYOaHlleHQ6bWhpa2JvaTcGGWh5ZXh0Om1oaWtib2k3XzE2NzAzMTM3NjIGDmh5ZXh0OjFlZjRod28yBhloeWV4dDoxZWY0aHdvMl8xNjcwMzEzNzYyBg5oeWV4dDo5eGl1aHZxNgYZaHlleHQ6OXhpdWh2cTZfMTY3MDMxMzc2MgYOaHlleHQ6aGpqNmlmb2IGGWh5ZXh0OmhqajZpZm9iXzE2NzAzMTM3NjIGDmh5ZXh0OnY5ODR0dG1jBhloeWV4dDp2OTg0dHRtY18xNjcwMzEzNzYyBg5oeWV4dDprMGxicHhtNQYZaHlleHQ6azBsYnB4bTVfMTY3MDMxMzc2MgYOaHlleHQ6OWwycDlkM3MGGWh5ZXh0OjlsMnA5ZDNzXzE2NzAzMTM3NjIGDmh5ZXh0Om5pZGFzMDBoBhloeWV4dDpuaWRhczAwaF8xNjcwMzEzNzYyBg5oeWV4dDptMWNlbXM2YQYZaHlleHQ6bTFjZW1zNmFfMTY3MDMxMzc2MgYOaHlleHQ6M3llcWNiazQGGWh5ZXh0OjN5ZXFjYms0XzE2NzAzMTM3NjIGDmh5ZXh0OnBjbmdhZmY3BhloeWV4dDpwY25nYWZmN18xNjcwMzEzNzYyBg5oeWV4dDptMHRreTByYwYZaHlleHQ6bTB0a3kwcmNfMTY3MDMxMzc2MgYOaHlleHQ6MGNlbno3YmoGGWh5ZXh0OjBjZW56N2JqXzE2NzAzMTM3NjIGDmh5ZXh0OjVkZmYzNmRyBhloeWV4dDo1ZGZmMzZkcl8xNjcwMzEzNzYyBg5oeWV4dDo2ZDZndngzNAYZaHlleHQ6NmQ2Z3Z4MzRfMTY3MDMxMzc2MgYOaHlleHQ6N2picTJmbWEGGWh5ZXh0OjdqYnEyZm1hXzE2NzAzMTM3NjIGDmh5ZXh0OmtxOXdlbG53BhloeWV4dDprcTl3ZWxud18xNjcwMzEzNzYyBg5oeWV4dDppN292cng4aAYZaHlleHQ6aTdvdnJ4OGhfMTY3MDMxMzc2MgYOaHlleHQ6Z3F0c2lkZjEGGWh5ZXh0OmdxdHNpZGYxXzE2NzAzMTM3NjIWACANNgBMXGYA";
        //String str = "AAMdAAEJwQAACcEQAyw8QgEM35ZWBXd1cHVpZhpnZXRVc2VyUGV0UmFjZUh1bnRlclJlY29yZH0AAQmHCAABBgR0UmVxHQABCXkKCgMAAAEXUHBqqhYgMGE4OWI0ODY1OWRjMDU2OTNhMDFmNmI3ZjdiNzA4YjUmADYad2ViaDUmMjUxMDMwMTA0OSZ3ZWJzb2NrZXRHAAAJG3ZwbGF5ZXJfc2Jhbm5lcl8xNjcwMzEzNzYyXzE2NzAzMTM3NjI9MTsgX195YW1pZF9uZXc9Q0I2QTc5Nzk0OTAwMDAwMThCQkZBNUUwMUMyMDY5RjA7IGdhbWVfZGlkPVlJd29Yd0lIYzNSNlhJdmNGVnV3RldhN0RIcG9IZGRSVWJNOyBfX3lhbWlkX3R0MT0wLjg1NjE2ODA2MjE1NTgxNzk7IF9xaW1laV91dWlkNDI9MTliMDExMjA5MWMxMDA3MDhlNmY1OTFjZjM1M2YyMTMwZjYzNDlhMzcyOyBTb3VuZFZhbHVlPTAuNTA7IGFscGhhVmFsdWU9MC44MDsgaXNJbkxpdmVSb29tPXRydWU7IHVkYl9ndWlkZGF0YT1lNzk3N2U4NDBmNWM0ODY0YjcxZDVjNGNmNzY3NjRjMTsgdWRiX2RldmljZWlkPXdfMTAzNzA2NTg2MzU1NjM5OTEwNDsgX3FpbWVpX2gzOD1lOTM1YTg4YjhlNmY1OTFjZjM1M2YyMTMwMzAwMDAwM2UxOWIwMTsgZ3VpZD0wYTg5YjQ4NjU5ZGMwNTY5M2EwMWY2YjdmN2I3MDhiNTsgdWRiX2Fub2JpenRva2VuPUFRQVFNd191YVpWTW1nM2FYVzJpNld6MHg3anRyd1NabTM3VmItbUZzMUhHVmE2dVhYVUFCSTJxRzY1SlNDRHJ0TF9VRTZ1YV9GUFgzaDh5Y0VaZUJnMnBkdW5OQUJXNzR2ZWdzaGxNcDFCVnk2b1lKMWZmbGt4YWhpVTZCNF9IY0h5NlcyQ3ZxNWJYMkpDSWtaeVk4aW50YlBJQUVaZjZIajJHTS15UWN0cnJGeThxcUJGdDB3alM1T01OdC1MeHNzOTVIUDh0aDlhN0ZORWxKOVJlWjJuQ2g4VWRDUlZUQi1tcWpMeGFHZDVwUll2U0dBb2xhU3REaUdDWDNMdDlnS3pQTjRvUGs4VU5YVDJnSVRKa3ltclFvNDktWGZtdk5MOXlLZ2c4M2RLQzU4eUdxNzBvb0JTS1RUcWZ5UW9GS2dXY25vMDluenVMbDFYR0NKYVdmM1lsOyB1ZGJfYW5vdWlkPTE0NzAzMTQ4Nzc0NTY7IGhkaWQ9Y2U5ZjFlOTMxNmRiNjI5YjdkZTY0NzVlOGNlNmI3MTIxNDUyOWFjNzsgZ3VpZD0wYTg5YjQ4NjU5ZGMwNTY5M2EwMWY2YjdmN2I3MDhiNTsgX3FpbWVpX2ZpbmdlcnByaW50PWM3M2Q0MTlkZjE5YjM0YjdmMzJlNDJlNjhhNDBlOGJkOyB1ZGJfcGFzc2RhdGE9MzsgX195YXNtaWQ9MC44NTYxNjgwNjIxNTU4MTc5OyBIbV9sdnRfNTE3MDBiNmM3MjJmNWJiNGNmMzk5MDZhNTk2ZWE0MWY9MTc2MTk5MTc3MSwxNzYyMDc4NjAwOyBITUFDQ09VTlQ9RTk4RUFFQkY5QUFBNTI4NTsgc2RpZD0wVW5IVWd2MF9xbWZENEtBS2x3emhxZnhyeGtMd1hNS0tzWGN2TXdXdlpjZ1ZvLUdXQnZtY2JTcTJ1MWQySmtmX0U1QUFYZG9hakJOYzc0a2ExeWJ0bUVQaWRvcnVieHo4LURmWXdzOW5zSlhXVmtuOUx0ZkZKd19RbzRrZ0tyOE9aSERxTm51d2c2MTJzR3lmbEZuMWRtWWxLZEQyV2NvNG9TZlRxZDRpVnVoTlYxdzJmd1NmU0I0TUYzaEtwOFp5OyB1ZGJfYml6dG9rZW49QVFBZkZZU0pVdDZKdEwzUW01cVlLT3NRVWV2WHhkVWdweG03VktuOEF1czBNQno1RmRnMlRLenJqUFhTc0tCcV9iNUE5WlFQNjM3T2paVktUN0xFQURHYUNNTjJoNGs5c21zWGpIRW1XZGswS0t5QTZkeVkwNFM2NWZpMl92Z1gyckdROVVWRGl4WUpDd3h6WGJxS0VaZ2gzbER6M1l4azJ4a0pfa0pxdWNNOFozVGJrYTVHQU1ueEo0RFlhZFhOc1JpSllXODMyNVlCTnhmRFdaU25ia09HNXI4czYzRmtBWGd3MVIyNURqODhFOGVoSkxPMFBrRWEtc2UwM2xCUktQdDBMNTJmTkR3V0VrbTVIUWlYTWtCM0laOHpkRkcwMkctVHZ6bGcwOU9BN2xDYldMdmlLT0d6YzJsaHVKd1pZUkJEbGxCNVNyV0kxb1NKX3hPV3JjbE47IHVkYl9jcmVkPUNoQUg2Y1FqQzhYSmNQN1IzM0lPU0FTVGhTQWF6d0JVWFVkZ1paSjl5VC1hbTZra1hIUXQwWC1UcndaeVJRcHlQaUt3X1F0dTNEbVZhc0VKUHhDQlBaWlpWbkx3THRGdl9KdXk0RDN2ZTFHVXA0bmpfYUxwdE9FOXpsUFpSbGZtR1g4cEg5M0ZTbjRjU1FHM2lmbGJZWGdrOyB1ZGJfb3JpZ2luPTE7IHVkYl9vdGhlcj0lN0IlMjJsdCUyMiUzQSUyMjE3NjIwNzg4MjY5MjIlMjIlMkMlMjJpc1JlbSUyMiUzQSUyMjElMjIlN0Q7IHVkYl9wYXNzcG9ydD1oeV8yNzE0NzEwNDk7IHVkYl9zdGF0dXM9MTsgdWRiX3VpZD0xMTk5NjQ1NDIwMjAyOyB1ZGJfdmVyc2lvbj0xLjA7IHVzZXJuYW1lPWh5XzI3MTQ3MTA0OTsgeXl1aWQ9MTE5OTY0NTQyMDIwMjsgcmVwX2NudD0xNDsgdWRiX2FjY2RhdGE9MDg2MTMxMTk1NDkyMzE7IF9feWFvbGR5eXVpZD0xMTk5NjQ1NDIwMjAyOyBfeWFzaWRzPV9fcm9vdHNpZCUzRENCNkFDQzgwRjAwMDAwMDFEMkYxMTM0QzFCNjBGMUQwOyBIbV9scHZ0XzUxNzAwYjZjNzIyZjViYjRjZjM5OTA2YTU5NmVhNDFmPTE3NjIwNzg4NTI7IGh1eWFzcF9yZXBfY250PTI1OyBodXlhX2ZsYXNoX3JlcF9jbnQ9MjE0OyBodXlhX3dlYl9yZXBfY250PTMzNTsgaHV5YV91YT13ZWJoNSYwLjAuMSZhY3Rpdml0eTsgaF91bnQ9MTc2MjA4ODQyMlxmAAsSY473IiABMGQLjJgMqAwsNgBMXGYA";
        byte[] temp = Base64.getDecoder().decode(str);
        logger.info("[<虎牙>] msg is:消息 进入游戏: {}", Base64.getEncoder().encodeToString(temp));
        ByteBuffer buffer = ByteBuffer.wrap(temp);
        try {
            session.getBasicRemote().sendBinary(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            connect();
        }
    }

//    public static void main(String[] args) {
//
//        GameYqlyyWsClient client = new GameYqlyyWsClient();
//        logger.debug("[一千零一夜<虎牙>]启动");
//        while (true) {
//            try {
//
//                //log.info("[reportGame1015WS] 上报 - 开始");
//                client.report();
//                Thread.sleep(1000 * 30);
//                //log.info("[reportGame1015WS] 上报 - 结束");
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    /**
     * 倍率映射（仅对宠物马拉松生效，gameId==22）：
     * 3->2, 6->5, 9->8, 18->17
     * 其他倍率保持不变
     */
    private static int mapRateOnlyForMarathon(int gameId, int rate) {
        if (gameId != 22) {
            return rate; // 非马拉松不映射
        }
        switch (rate) {
            case 3:
                return 2;
            case 6:
                return 5;
            case 9:
                return 8;
            case 18:
                return 17;
            default:
                return rate;
        }
    }





    public static String aesBase64ByCBC(String sSrc, String sKey, String vector) {
        Cipher cipher;
        try {
            byte[] raw = sKey.getBytes("UTF-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec iv = new IvParameterSpec(vector.getBytes("UTF-8"));// 使用CBC模式，需要一个向量iv，可增加加密算法的强度
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            byte[] encrypted = cipher.doFinal(sSrc.getBytes("UTF-8"));
            return org.apache.commons.codec.binary.Base64.encodeBase64String(encrypted);
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
        }
        return null;
    }


}
