package com.entity.AccountedNotify;

import com.qq.tars.protocol.tars.TarsInputStream;
import com.qq.tars.protocol.tars.TarsOutputStream;
import com.qq.tars.protocol.tars.TarsStructBase;

//7103
public class TreasureHunterInfoItem extends TarsStructBase {

    public int iProb;
    public int iProbRate;
    public int iRate;
    public int iTreasureId;
    public int iWeight;
    public long lBetClues;
    public String sTag;
    public String sTreasureIcon;
    public String sTreasureName;
    public DirectSendTreasureBetSum vTreasureBetSumDetail;

    public DirectSendTreasureBetSum getvTreasureBetSumDetail() {
        return vTreasureBetSumDetail;
    }

    public void setvTreasureBetSumDetail(DirectSendTreasureBetSum vTreasureBetSumDetail) {
        this.vTreasureBetSumDetail = vTreasureBetSumDetail;
    }




    public String getsTreasureName() {
        return sTreasureName;
    }

    public void setsTreasureName(String sTreasureName) {
        this.sTreasureName = sTreasureName;
    }

    public String getsTreasureIcon() {
        return sTreasureIcon;
    }

    public void setsTreasureIcon(String sTreasureIcon) {
        this.sTreasureIcon = sTreasureIcon;
    }

    public String getsTag() {
        return sTag;
    }

    public void setsTag(String sTag) {
        this.sTag = sTag;
    }

    public long getlBetClues() {
        return lBetClues;
    }

    public void setlBetClues(long lBetClues) {
        this.lBetClues = lBetClues;
    }

    public int getiWeight() {
        return iWeight;
    }

    public void setiWeight(int iWeight) {
        this.iWeight = iWeight;
    }

    public int getiTreasureId() {
        return iTreasureId;
    }

    public void setiTreasureId(int iTreasureId) {
        this.iTreasureId = iTreasureId;
    }

    public int getiRate() {
        return iRate;
    }

    public void setiRate(int iRate) {
        this.iRate = iRate;
    }

    public int getiProbRate() {
        return iProbRate;
    }

    public void setiProbRate(int iProbRate) {
        this.iProbRate = iProbRate;
    }

    public int getiProb() {
        return iProb;
    }

    public void setiProb(int iProb) {
        this.iProb = iProb;
    }


    @Override
    public void writeTo(TarsOutputStream os) {

    }

    @Override
    public void readFrom(TarsInputStream is) {
        System.out.println(">>>>>>>>>>>>TreasureHunterInfoItem>>>>>>>>>>>");
        this.iTreasureId=is.read(this.iTreasureId,0,false);
        this.sTreasureName=is.read(this.sTreasureName,1,false);
        this.sTag=is.read(this.sTag,3,false);
        this.iWeight=is.read(this.iWeight,4,false);
        this.iRate=is.read(this.iRate,5,false);
        this.iProb=is.read(this.iProb,6,false);
        this.iProbRate=is.read(this.iProbRate,7,false);
        this.lBetClues=is.read(this.lBetClues,8,false);
        //this.vTreasureBetSumDetail=(DirectSendTreasureBetSum)is.read(this.vTreasureBetSumDetail,9,false);
    }
}
