package com.game.ylbwz;


public class TestYlbwzWebsocketClient {

    public static void main(String[] args) throws InterruptedException {
        String webSocketUri = "ws://changchang.maxxiang.com/chang";
        YlbwzWebSocketClient client1 = YlbwzWebSocketClient.getInstance(webSocketUri);
        client1.sendMessage("{\"action\":1,\"session\":\"12b852b4-8a56-47a2-ad3d-b69cf3914488\"}");
        while (true) {
            client1.sendMessage("{\"action\":777,\"session\":\"12b852b4-8a56-47a2-ad3d-b69cf3914488\",\"phone\":\"15641737909\"}");
            Thread.sleep(10000);
        }


    }
}
