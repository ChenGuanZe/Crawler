package com.entity.AccountedNotify;

import com.qq.tars.protocol.tars.TarsInputStream;
import com.qq.tars.protocol.tars.TarsOutputStream;
import com.qq.tars.protocol.tars.TarsStructBase;

import java.util.ArrayList;
import java.util.List;

public class OpenTreasureHunter extends TarsStructBase {
    private long lOldRoundId;
    private long lOldRoundIndexEndTime;
    private long lOldRoundIndexTime;
    private long lServerTime;
    //private TreasureHunterInfo vTreasure=new TreasureHunterInfo();
    public List<Object> vTreasure=new ArrayList<>();
    public OpenTreasureHunter(){
        vTreasure.add(new TreasureHunterInfoItem());
        vTreasure.add(new ArrayList<Proto>());
    }


    public List<Object> getvTreasure() {
        return vTreasure;
    }

    public void setvTreasure(List<Object> vTreasure) {
        this.vTreasure = vTreasure;
    }




    public long getlServerTime() {
        return lServerTime;
    }

    public void setlServerTime(long lServerTime) {
        this.lServerTime = lServerTime;
    }

    public long getlOldRoundIndexTime() {
        return lOldRoundIndexTime;
    }

    public void setlOldRoundIndexTime(long lOldRoundIndexTime) {
        this.lOldRoundIndexTime = lOldRoundIndexTime;
    }

    public long getlOldRoundIndexEndTime() {
        return lOldRoundIndexEndTime;
    }

    public void setlOldRoundIndexEndTime(long lOldRoundIndexEndTime) {
        this.lOldRoundIndexEndTime = lOldRoundIndexEndTime;
    }

    public long getlOldRoundId() {
        return lOldRoundId;
    }

    public void setlOldRoundId(long lOldRoundId) {
        this.lOldRoundId = lOldRoundId;
    }


    @Override
    public void writeTo(TarsOutputStream os) {

    }

    @Override
    public void readFrom(TarsInputStream is) {
        this.lOldRoundId=is.read(this.getlOldRoundId(),0,false);
        System.out.println("lOldRoundId:"+lOldRoundId);
        //vTreasure=new TreasureHunterInfo();

        this.setvTreasure(is.readArray(this.getvTreasure(),4,false));
        System.out.println("lOldRoundId:"+lOldRoundId);

    }
}
