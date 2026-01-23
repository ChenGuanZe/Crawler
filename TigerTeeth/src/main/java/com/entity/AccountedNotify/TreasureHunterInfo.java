package com.entity.AccountedNotify;

import com.qq.tars.protocol.tars.TarsInputStream;
import com.qq.tars.protocol.tars.TarsOutputStream;
import com.qq.tars.protocol.tars.TarsStructBase;

import java.util.ArrayList;
import java.util.List;

public class TreasureHunterInfo extends TarsStructBase {
    public TreasureHunterInfoItem proto=new TreasureHunterInfoItem();
    public List<TreasureHunterInfoItem> value=new ArrayList<>();

    public List<TreasureHunterInfoItem> getValue() {
        return value;
    }

    public void setValue(List<TreasureHunterInfoItem> value) {
        this.value = value;
    }

    public TreasureHunterInfoItem getProto() {
        return proto;
    }

    public void setProto(TreasureHunterInfoItem proto) {
        this.proto = proto;
    }



    @Override
    public void writeTo(TarsOutputStream os) {

    }

    @Override
    public void readFrom(TarsInputStream is) {
        proto=new TreasureHunterInfoItem();
        //value=new ArrayList<>();
        proto.readFrom(is);
    }
}
