package com.game.gcbwz;


public class TestBwhdWebsocketClient {

    public static void main(String[] args) throws InterruptedException {
        String webSocketUri = "ws://tulong.maxxiang.com/moriwss";
        BwhdWebSocketClient client1 = BwhdWebSocketClient.getInstance(webSocketUri);
        client1.sendMessage("{\"action\":1,\"session\":\"b0dcc0a3-2a68-4e48-8b23-51e58da92e82\"}");
        while (true) {
            client1.sendMessage("{\"action\":777,\"session\":\"b0dcc0a3-2a68-4e48-8b23-51e58da92e82\",\"phone\":\"15641737909\"}");
            Thread.sleep(10000);
        }
    }
}
