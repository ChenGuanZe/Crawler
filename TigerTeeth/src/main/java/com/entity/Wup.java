package com.entity;

import java.util.HashMap;
import java.util.Map;

public class Wup {
    public short iVersion = 3;
    public byte cPacketType = 0;
    public int iMessageType = 0;
    public int iRequestId = 0;
    public String sServantName = "";
    public String sFuncName = "";
    public byte[] sBuffer;
    public int iTimeout = 0;
    public Map<String,String> context=new HashMap<String, String>() {{
        put("kproto", "");
        put("vproto", "");
        //put("value",new Object());
    }};
    public Map<String,String> status=new HashMap<String, String>() {{
        put("kproto", "");
        put("vproto", "");
        //put("value",new Object());
    }};
    public Map<String,Object> data;
    public Map<String,Object> newdata;

}
