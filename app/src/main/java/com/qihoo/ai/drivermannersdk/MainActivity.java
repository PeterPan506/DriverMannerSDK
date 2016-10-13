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
    public void DetectCollision(String string) {
        Log.e(TAG, "Receiving: "+string);
    }

    @Override
    public void DetectMove(int x, int y, int z) {
        Log.e(TAG, "Is move detected"+ x +" " + y +" " + z);
    }

    @Override
    public void DetectBrake() {
        Log.e(TAG, "Detected sharp brake");
    }

    @Override
    public void DetectAccelerate() {
        stepcount++;
        stepCount.setText(stepcount+"");
        Log.e(TAG, "Detected fast accelerate"+stepcount);
    }

    @Override
    public void DetectTurn() {
        Log.e(TAG, "Detected sudden steering");
    }
}
