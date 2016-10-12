package com.qihoo.ai.drivermannersdk;

/**
 * Created by panjunwei-iri on 2016/10/12.
 */

public interface DetectCallBack {
    void DetectCollision(String string);

    void DetectMove(int x, int y, int z);
}
