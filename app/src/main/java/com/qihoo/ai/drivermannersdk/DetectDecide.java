package com.qihoo.ai.drivermannersdk;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.renderscript.ScriptIntrinsicYuvToRGB;
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

    private float[][] oriValues = new float[4][sampleCnt];
    private float[][] tmpValues = new float[4][sampleCnt];
    private float[][] fftValues = new float[4][sampleCnt];
    private float[][] fftTmp = new float[4][dealCnt];
    private float[][] bufValues = new float[4][dealCnt];
    private long[] timeNow = new long[sampleCnt];
    private long[] timeTmp = new long[dealCnt];
    private long[] timeBuf = new long[dealCnt];
    private final int valueNum = 4;
    //用于存放计算阈值的波峰波谷差值
    private float[] tempValue = new float[valueNum];
    private int tempCount = 0;
    //是否上升的标志位
    private boolean isDirectionUp = false;
    //持续上升次数
    private int continueUpCount = 0;
    //上一点的持续上升的次数，为了记录波峰的上升次数
    private int continueUpFormerCount = 0;
    //上一点的状态，上升还是下降
    private boolean lastStatus = false;
    //波峰值
    private float peakOfWave = 0;
    //波谷值
    private float valleyOfWave = 0;
    //此次波峰的时间
    private long timeOfThisPeak = 0;
    //上次波峰的时间
    private long timeOfLastPeak = 0;
    //当前的时间
    private long timeOfNow = 0;
    //当前传感器的值
    private float gravityNew = 0;
    //上次传感器的值
    private float gravityOld = 0;
    //动态阈值需要动态的数据，这个值用于这些动态数据的阈值
    private final float initialValue = (float) 1.3;
    //初始阈值
    private float ThresholdValue = (float) 2.0;
    private static int winCnt = 0;
    private static int stepCnt = 0;
    private static int dataIndex = 0;


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
                            fftFilter(oriValues[i], oriValues[i].length, fftValues[i]);
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
                            fftFilter(oriValues[i], oriValues[i].length, fftValues[i]);
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
     * 检测步子，并开始计步
     * 1.传入sersor中的数据
     * 2.如果检测到了波峰，并且符合时间差以及阈值的条件，则判定为1步
     * 3.符合时间差条件，波峰波谷差值大于initialValue，则将该差值纳入阈值的计算中
     * */
    private void DetectorNewStep(float values) {
        if (gravityOld == 0) {
            gravityOld = values;
        } else {
            if (DetectorPeak(values, gravityOld)) {
                timeOfLastPeak = timeOfThisPeak;
                timeOfNow = System.currentTimeMillis();
                if (timeOfNow - timeOfLastPeak >= 250
                        && (peakOfWave - valleyOfWave >= ThresholdValue)) {
                    timeOfThisPeak = timeOfNow;
                    /*
                     * 更新界面的处理，不涉及到算法
                     * 一般在通知更新界面之前，增加下面处理，为了处理无效运动：
                     * 1.连续记录10才开始计步
                     * 2.例如记录的9步用户停住超过3秒，则前面的记录失效，下次从头开始
                     * 3.连续记录了9步用户还在运动，之前的数据才有效
                     * */
//                    mStepListeners.onStep();
                    mDetectCallBack.DetectAccelerate();
                }
                if (timeOfNow - timeOfLastPeak >= 250
                        && (peakOfWave - valleyOfWave >= initialValue)) {
                    timeOfThisPeak = timeOfNow;
                    ThresholdValue = Peak_Valley_Thread(peakOfWave - valleyOfWave);
                }
            }
        }
        gravityOld = values;
    }

    /*
     * 检测波峰
     * 以下四个条件判断为波峰：
     * 1.目前点为下降的趋势：isDirectionUp为false
     * 2.之前的点为上升的趋势：lastStatus为true
     * 3.到波峰为止，持续上升大于等于2次
     * 4.波峰值大于20
     * 记录波谷值
     * 1.观察波形图，可以发现在出现步子的地方，波谷的下一个就是波峰，有比较明显的特征以及差值
     * 2.所以要记录每次的波谷值，为了和下次的波峰做对比
     * */
    private boolean DetectorPeak(float newValue, float oldValue) {
        lastStatus = isDirectionUp;
        if (newValue >= oldValue) {
            isDirectionUp = true;
            continueUpCount++;
        } else {
            continueUpFormerCount = continueUpCount;
            continueUpCount = 0;
            isDirectionUp = false;
        }

        if (!isDirectionUp && lastStatus
                && (continueUpFormerCount >= 2 || oldValue >= 20)) {
            peakOfWave = oldValue;
            return true;
        } else if (!lastStatus && isDirectionUp) {
            valleyOfWave = oldValue;
            return false;
        } else {
            return false;
        }
    }

    /*
     * 阈值的计算
     * 1.通过波峰波谷的差值计算阈值
     * 2.记录4个值，存入tempValue[]数组中
     * 3.在将数组传入函数averageValue中计算阈值
     * */
    private float Peak_Valley_Thread(float value) {
        float tempThread = ThresholdValue;
        if (tempCount < valueNum) {
            tempValue[tempCount] = value;
            tempCount++;
        } else {
            tempThread = averageValue(tempValue, valueNum);
            for (int i = 1; i < valueNum; i++) {
                tempValue[i - 1] = tempValue[i];
            }
            tempValue[valueNum - 1] = value;
        }
        return tempThread;

    }

    /*
     * 梯度化阈值
     * 1.计算数组的均值
     * 2.通过均值将阈值梯度化在一个范围里
     * 3.参数暂时不开放（a,b,c,d,e,f,g,h,i,i,k,l）
     * */
    private float averageValue(float value[], int n) {
        float ave = 0;
        for (int i = 0; i < n; i++) {
            ave += value[i];
        }
        ave = ave / valueNum;
        if (ave >= 8.0)
            ave = 4.3F;
        else if (ave >= 7.0 && ave < 8.0)
            ave = 3.3F;
        else if (ave >= 4.0 && ave < 7.0)
            ave = 2.3F;
        else if (ave >= 3.0 && ave < 4.0)
            ave = 2.0F;
        else {
            ave = 1.3F;
        }
        return ave;
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

    private void fftFilter(float[] oriSignal, int num, float result[]){
        Complex[] x = new Complex[num];
        Complex zero = new Complex(0,0);
        for(int i = 0; i < num; i++){
            x[i] = new Complex(oriSignal[i], 0);
        }
        Complex[] xFFT = fft(x);
        for(int i = 100; i< sampleCnt; i++){ //100是参数，之后抽取出来，表示滤波精度
            xFFT[i] = zero;
        }
        Complex[] xIFFT = ifft(xFFT);
        for(int i = 0; i< num; i++) {
            result[i] = (float) xIFFT[i].re();//把fftValues提出去，作为返回值
        }
    }
}
