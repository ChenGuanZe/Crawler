package com.entity;

public class WsCmd {
    private int iCmdType;
    private byte[] vData;
    private long lRequestId;
    private String traceId;
    private int iEncryptType;
    private long lTime;

    public String getsMD5() {
        return sMD5;
    }

    public void setsMD5(String sMD5) {
        this.sMD5 = sMD5;
    }

    public long getlTime() {
        return lTime;
    }

    public void setlTime(long lTime) {
        this.lTime = lTime;
    }

    public int getiEncryptType() {
        return iEncryptType;
    }

    public void setiEncryptType(int iEncryptType) {
        this.iEncryptType = iEncryptType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getlRequestId() {
        return lRequestId;
    }

    public void setlRequestId(long lRequestId) {
        this.lRequestId = lRequestId;
    }

    public byte[] getvData() {
        return vData;
    }

    public void setvData(byte[] vData) {
        this.vData = vData;
    }

    public int getiCmdType() {
        return iCmdType;
    }

    public void setiCmdType(int iCmdType) {
        this.iCmdType = iCmdType;
    }

    private String sMD5;
}
