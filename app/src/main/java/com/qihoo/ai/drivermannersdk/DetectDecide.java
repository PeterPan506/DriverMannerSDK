package com.qihoo.ai.drivermannersdk;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.Context.SENSOR_SERVICE;
import static com.qihoo.ai.drivermannersdk.FFT.fft;
import static com.qihoo.ai.drivermannersdk.FFT.ifft;

/**
 * Created by panjunwei-iri on 2016/10/12.
 */

public class DetectDecide implements SensorEventListener{
    private static final String TAG = DetectDecide.class.getSimpleName();
    final static SimpleDateFormat DF = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
    private String[] title = { "Time","X_Ori", "Y_Ori", "Z_Ori", "|V|_Ori", "X_FFT" , "Y_FFT", "Z_FFT", "|V|_FFT"};
    private File xlsFile;
    private String xlsFileName;
    private DetectCallBack mDetectCallBack;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    private Context mContext;

    private static final int sampleCnt = 2048;
    private static final int dealCnt = 200;
    private static final int valueNum = 4;

    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static int winCnt = 0;
    private static int stepCnt = 0;
    private static int dataIndex = 0;

    private float[][] oriValues = new float[valueNum][sampleCnt];
    private float[][] tmpValues = new float[valueNum][sampleCnt];
    private float[][] fftValues = new float[valueNum][sampleCnt];
    private float[][] fftTmp = new float[valueNum][dealCnt];
    private float[][] bufValues = new float[valueNum][dealCnt];
    private long[] timeNow = new long[sampleCnt];
    private long[] timeTmp = new long[dealCnt];
    private long[] timeBuf = new long[dealCnt];
    private float[] baseOfWave = new float[valueNum];

    //加速、减速及转弯检测的阈值，该阈值根据实际情况调整
    private float accThresValue = (float) 1.0;
    private float decThresValue = (float) 2.0;
    private float turThresValue = (float) 1.5;


    //是否上升的标志位
    private boolean isDirectionUp[] = {false, false, false};
    //持续上升次数
    private int continueUpCount[] = new int[3];
    //持续下降次数
    private int continueDownCount[] = new int[3];
    //上一点的持续上升的次数，为了记录波峰的上升次数
    private int continueUpFormerCount[] = new int[3];
    //上一点的持续下降的次数，为了记录波峰的下降次数
    private int continueDownFormerCount[] = new int[3];
    //上一点的状态，上升还是下降
    private boolean lastStatus[] = {false, false, false};
    //波峰值
    private float peakOfWave[] = new float[3];
    //波谷值
    private float valleyOfWave[] = new float[3];
    //此次波峰的时间
    private long timeOfThisPeak[] = new long[3];
    //上次波峰的时间
    private long timeOfLastPeak[] = new long[3];
    //此次波谷的时间
    private long timeOfThisValley[] = new long[3];
    //上次波峰谷的时间
    private long timeOfLastValley[] = new long[3];
    //当前的时间
    private long timeOfNow = 0;
    //上次传感器的值
    private float gravityOld[] = new float[3];

    DetectDecide(Context context){
        mContext = context;
    }

    public void registerGsensor(final DetectDecide detectDecide, DetectCallBack detectCallBack){
        mDetectCallBack = detectCallBack;
        new Thread(new Runnable() {
            public void run() {
                mSensorManager = (SensorManager)mContext.getSystemService(SENSOR_SERVICE);
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                mSensorManager.registerListener(detectDecide, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
                initExcel();//初始化Excel表格
            }
        }).start();
    }

    public void unregisterGsensor(DetectDecide detectDecide){
        mSensorManager.unregisterListener(detectDecide);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        timeOfNow = System.currentTimeMillis();
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if(winCnt == sampleCnt){
                for(int i = 0;i<3;i++){
                    tmpValues[i][stepCnt] = event.values[i];
                }
                tmpValues[3][stepCnt] = (float) Math.sqrt(tmpValues[0][stepCnt] * tmpValues[0][stepCnt]
                        + tmpValues[1][stepCnt] * tmpValues[1][stepCnt]
                        + tmpValues[2][stepCnt] * tmpValues[2][stepCnt]);
                timeTmp[stepCnt] = timeOfNow;
                DetectorNewStep(fftTmp[2][stepCnt], timeBuf[stepCnt], Z);//Z轴，注意判断的是上dealCnt的值，
                // 也就是说说会延后这么长时间，目的是讲任务分散开，避免任务过于集中影响效率。
                // timeBuf也是上一周期相应的时间点
                stepCnt++;
            }else {
                for (int i = 0; i < 3; i++) {
                    oriValues[i][winCnt] = event.values[i];
                }
                oriValues[3][winCnt] = (float) Math.sqrt(oriValues[0][winCnt] * oriValues[0][winCnt]
                        + oriValues[1][winCnt] * oriValues[1][winCnt]
                        + oriValues[2][winCnt] * oriValues[2][winCnt]);
                timeNow[winCnt] = timeOfNow;
                winCnt++;
                if(winCnt == sampleCnt){
                    if(xlsFileName != null){
                        ExcelUtils.writeOneArrayToExcel(timeNow, xlsFileName,dataIndex,0);
                        ExcelUtils.writeArrayToExcel(oriValues, xlsFileName, dataIndex, 1);
                        for(int i = 0; i <4; i++) {
                            baseOfWave[i] = fftFilter(oriValues[i], oriValues[i].length, fftValues[i]);
                        }
                        ExcelUtils.writeArrayToExcel(fftValues, xlsFileName, dataIndex, 5);
//                        Log.e(TAG, "writeOArrayToExcel");
                        dataIndex = winCnt-dealCnt;
                    }
                }
                Log.e(TAG, "winCnt = "+ winCnt );
                //程序运行采集到4000点作为窗口，进行第一次FFT滤波，此后每采集100个数据替换原来4000点中最老的100个点
                //然后进行FFT滤波，这样可以得到可用的G-Sensor数据Value[0-3]分别代表X，Y，Z和V
            }
            if(stepCnt == dealCnt){
                dataIndex += dealCnt;
//                ExcelUtils.writeOArrayToExcel(tmpValues, xlsFileName, dataIndex, 0);

                int n = sampleCnt - stepCnt;
                System.arraycopy(timeNow, stepCnt, timeNow, 0, n);
                System.arraycopy(timeTmp, 0, timeNow, n, stepCnt);
                System.arraycopy(timeTmp, 0, timeBuf, 0, stepCnt);
                for(int i = 0; i<4;i++){
                    System.arraycopy(oriValues[i], stepCnt, oriValues[i], 0, n);
                    System.arraycopy(tmpValues[i], 0, oriValues[i], n, stepCnt);
                    System.arraycopy(tmpValues[i], 0, bufValues[i], 0, stepCnt);
                }
                new Thread(new Runnable() {
                    public void run() {
//                        此处进行FFT滤波，得到可用的G-Sensor数值
                        for(int i = 0; i < 4; i++) {
                            baseOfWave[i] = fftFilter(oriValues[i], oriValues[i].length, fftValues[i]);
                            System.arraycopy(fftValues[i], sampleCnt-dealCnt, fftTmp[i], 0,dealCnt);
                        }
                        ExcelUtils.writeOneArrayToExcel(timeBuf, xlsFileName,dataIndex,0);
                        ExcelUtils.writeArrayToExcel(bufValues, xlsFileName, dataIndex, 1);
                        ExcelUtils.writeArrayToExcel(fftTmp, xlsFileName, dataIndex, 5);
                    }
                }).start();
//                DetectorNewStep(gravityNew);
                stepCnt = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /*
     * 检测加减速
     * 1.传入sersor中相应轴的数据，以及该数据的时间
     * 2.如果检测到了波峰，并且符合时间差以及阈值的条件，则判定为1次加速，反之为减速
     * */
    private void DetectorNewStep(float values, long currTime, int axis) {
        if(axis>2 || axis <0){
            return;
        }
        if (gravityOld[axis] == 0) {
            gravityOld[axis] = values;
        } else {
            switch (axis) {
                case Z:
                    if (DetectorPeak(values, gravityOld[axis], axis)) {
                        timeOfLastPeak[axis] = timeOfThisPeak[axis];
                        timeOfLastValley[axis] = timeOfThisValley[axis];
                        //加速判断
                        if (currTime - timeOfLastPeak[axis] >= 1000
                                && (peakOfWave[axis] - baseOfWave[2] >= accThresValue)) {
                            timeOfThisPeak[axis] = currTime;
                            mDetectCallBack.DetectAccelerate(values, timeOfThisPeak[axis]);
                        }
                        //减速判断
                        if (currTime - timeOfLastValley[axis] >= 1000
                                && (baseOfWave[2] - valleyOfWave[axis] >= decThresValue)) {
                            timeOfThisValley[axis] = currTime;
                            mDetectCallBack.DetectBrake(values, timeOfThisValley[axis]);
                        }
                    }
                    break;
                case X:
                    if (DetectorPeak(values, gravityOld[axis], axis)) {
                        timeOfLastPeak[axis] = timeOfThisPeak[axis];
                        if(currTime - timeOfLastPeak[axis] > 1000
                                &&(peakOfWave[axis] - valleyOfWave[axis] >= turThresValue)){
                            timeOfLastPeak[axis] = currTime;
                            mDetectCallBack.DetectTurn(values,timeOfThisValley[axis]);
                        }
                    }
                    break;
            }
        }
        gravityOld[axis] = values;
    }

    /*
     * 检测波峰
     * 以下3个条件判断为波峰：
     * 1.目前点为下降的趋势：isDirectionUp为false
     * 2.之前的点为上升的趋势：lastStatus为true
     * 3.到波峰为止，持续上升大于等于10次
     * 记录波谷值
     * 1.观察波形图，可以发现在出现步子的地方，波谷的下一个就是波峰，有比较明显的特征以及差值
     * 2.所以要记录每次的波谷值，为了和下次的波峰做对比
     * */
    private boolean DetectorPeak(float newValue, float oldValue, int axis) {
        if(axis>2 || axis <0){
            return false;
        }
        lastStatus = isDirectionUp;
        if (newValue >= oldValue) {
            continueDownFormerCount = continueDownCount;
            isDirectionUp[axis] = true;
            continueUpCount[axis]++;
            continueDownCount[axis] = 0;
        } else {
            continueUpFormerCount = continueUpCount;
            continueDownCount[axis]++;
            continueUpCount[axis] = 0;
            isDirectionUp[axis] = false;
        }

        if (!isDirectionUp[axis] && lastStatus[axis]
                && continueUpFormerCount[axis] >= 10 ) {
            peakOfWave[axis] = oldValue;
            return true;
        } else if (!lastStatus[axis] && isDirectionUp[axis]
                && continueDownFormerCount[axis] >= 10) {
            valleyOfWave[axis] = oldValue;
            return true;
        } else {
            return false;
        }
    }

    private void initExcel() {
//        xlsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator+"360DriveApi/");
        makeDir();
        Log.e(TAG, xlsFile.toString());
        xlsFileName = xlsFile + File.separator + "360_CAR" +DF.format(new Date())+".xls";
        Log.e(TAG, xlsFileName);
        ExcelUtils.initExcel(xlsFileName, title);
    }


    private boolean makeDir() {
        xlsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator+"360DriveApi/");
        if (!xlsFile.exists()) {
            return xlsFile.mkdirs();
        }
        return true;
    }

    private float fftFilter(float[] oriSignal, int num, float result[]){
        Complex[] x = new Complex[num];
        Complex zero = new Complex(0,0);
        for(int i = 0; i < num; i++){
            x[i] = new Complex(oriSignal[i], 0);
        }
        Complex[] xFFT = fft(x);
        for(int i = 100; i< sampleCnt; i++){ //100是参数，之后抽取出来，表示滤波精度
            xFFT[i] = zero;
        }
        float baseOfWave = (float)xFFT[0].re();
        Complex[] xIFFT = ifft(xFFT);
        for(int i = 0; i< num; i++) {
            result[i] = (float) xIFFT[i].re();//把fftValues提出去，作为返回值
        }
        return baseOfWave;
    }
}
