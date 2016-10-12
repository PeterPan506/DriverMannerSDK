package com.qihoo.ai.drivermannersdk;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.Calendar;

import static android.content.Context.SENSOR_SERVICE;

/**
 * Created by panjunwei-iri on 2016/10/12.
 */

public class DetectDecide implements SensorEventListener{
    private static final String TAG = DetectDecide.class.getSimpleName();
    private static DetectCallBack mDetectCallBack;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    private int mX, mY, mZ;
    private long lasttimestamp = 0;
    Calendar mCalendar;
    Context mContext;

//    DetectDecide(Context context){
    DetectDecide(){
        super();
//        mContext = context;
    }

    public static void setDetectCallBack(DetectCallBack detectCallBack) {
        mDetectCallBack = detectCallBack;
    }

    public static void doCallBackMethod(){
        String info = "This is the message will de sent";
        mDetectCallBack.DetectCollision(info);
    }

    public void registerGsensor(){
//        mSensorManager = (SensorManager)mContext.getSystemService(SENSOR_SERVICE);
//        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void unregisterGsensor(){
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            int x = (int)event.values[0];
            int y = (int)event.values[1];
            int z = (int)event.values[2];
            mCalendar = Calendar.getInstance();
            long stamp = mCalendar.getTimeInMillis()/10001;

            int second = mCalendar.get(Calendar.SECOND);

            int px = Math.abs(mX - x);
            int py = Math.abs(mY - y);
            int pz = Math.abs(mZ - z);

            Log.d(TAG, "pX:" + px + "  pY:" + py + "  pZ:" + pz + "    stamp:"
                    + stamp + "  second:" + second);
            int maxvalue = getMaxValue(px, py, pz);

            if (maxvalue > 2 && (stamp - lasttimestamp) > 30) {
                lasttimestamp = stamp;
                Log.d(TAG, " sensor isMoveorchanged....");
            }
            if(x>5){
                mDetectCallBack.DetectMove(mX, mY, mZ);
            }

            mX = x;
            mY = y;
            mZ = z;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public int getMaxValue(int px, int py, int pz) {
        int max = 0;
        if (px > py && px > pz) {
            max = px;
        } else if (py > px && py > pz) {
            max = py;
        } else if (pz > px && pz > py) {
            max = pz;
        }

        return max;
    }
}
