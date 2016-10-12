package com.qihoo.ai.drivermannersdk;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity implements DetectCallBack{
    private static final String TAG = MainActivity.class.getSimpleName();
    private DetectDecide mDetectDecide;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DetectDecide.setDetectCallBack(this);
        mDetectDecide = new DetectDecide();
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mDetectDecide, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mDetectDecide.unregisterGsensor();
        mSensorManager.unregisterListener(mDetectDecide);
    }

    @Override
    public void DetectCollision(String string) {
        Log.e(TAG, "Receiving: "+string);
    }

    @Override
    public void DetectMove(int x, int y, int z) {
        Log.e(TAG, "Is move detected"+ x +" " + y +" " + z);
    }
}
