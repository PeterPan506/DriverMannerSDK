package com.qihoo.ai.drivermannersdk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements DetectCallBack{
    private static final String TAG = MainActivity.class.getSimpleName();
    private DetectDecide mDetectDecide;
    private TextView stepCount;
    private static int stepcount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepCount = (TextView)findViewById(R.id.stepcnt);

        mDetectDecide = new DetectDecide(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mSensorManager.registerListener(mDetectDecide, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mDetectDecide.registerGsensor(mDetectDecide, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mDetectDecide.unregisterGsensor();
//        mSensorManager.unregisterListener(mDetectDecide);
        mDetectDecide.unregisterGsensor(mDetectDecide);
    }

    @Override
    public void DetectBrake(float values, long time) {
        Log.e(TAG, "Detected sudden brake");
    }

    @Override
    public void DetectAccelerate(float values, long time) {
        Log.e(TAG, "Detected sudden accelerate");
    }

    @Override
    public void DetectTurn(float values, long time) {
        Log.e(TAG, "Detected sudden steering");
    }
}
