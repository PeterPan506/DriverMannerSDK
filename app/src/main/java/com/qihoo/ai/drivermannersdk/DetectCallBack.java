package com.qihoo.ai.drivermannersdk;

/**
 * Created by panjunwei-iri on 2016/10/12.
 */

public interface DetectCallBack {

    void DetectBrake(float values, long time);

    void DetectAccelerate(float values, long time);

    void DetectTurn(float values, long time);
}
