package io.github.mingrizaizao.androidlightgun;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import android.widget.Toast;
import android.widget.SeekBar;
import java.util.Collections;
import java.util.List;

import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothDevice;
import java.util.concurrent.Executors;
import android.widget.Button;
import android.view.MotionEvent;


public class LightGunActivity extends CameraActivity implements CvCameraViewListener2 {


    private static final String    TAG = "LightGun::Activity";

    private Mat                    mRgba;
    private Mat                    mGray;

    private MyJavaCameraView   mOpenCvCameraView;

    private BluetoothHidDevice mHidDevice;

    private BluetoothDevice mHostDevice;
    private float[] mTargetCoords = new float[3]; // [x, y, found]
    private Handler mHandler = new Handler();
    private static final int UPDATE_INTERVAL = 20; // 50ms更新一次
    private volatile boolean btn1_pressed = false;
    private Button mBtnShoot;
    private boolean mIsButtonPressed = false;

// ==================== MIC 音频监视 ====================

    private AudioMonitor mAudioMonitor;

// ==================== 二值化阈值 ====================

    private SeekBar mThresholdSeekBar;

    // 默认二值化阈值
    private volatile int mThreshold = 220;

    private static final int AUDIO_PERMISSION_REQUEST = 2001;

    // 1. 修正HID描述符 - 添加Report ID
    private static final byte[] LIGHTGUN_DESCRIPTOR = {
            0x05, 0x01,        // Usage Page (Generic Desktop Ctrls)
            0x09, 0x02,        // Usage (Mouse)
            (byte)0xA1, 0x01,  // Collection (Application)
            0x09, 0x01,        //   Usage (Pointer)
            (byte)0xA1, 0x00,  //   Collection (Physical)
            (byte)0x85, 0x01,        //     Report ID (1)  <- 关键！必须和SendReport匹配
            0x05, 0x09,        //     Usage Page (Button)
            0x19, 0x01,        //     Usage Minimum (0x01)
            0x29, 0x03,        //     Usage Maximum (0x03)
            0x15, 0x00,        //     Logical Minimum (0)
            0x25, 0x01,        //     Logical Maximum (1)
            (byte) 0x95, 0x03,        //     Report Count (3)
            0x75, 0x01,        //     Report Size (1)
            (byte)0x81, 0x02,  //     Input (Data,Var,Abs)
            (byte)0x95, 0x01,        //     Report Count (1)
            0x75, 0x05,        //     Report Size (5)
            (byte)0x81, 0x03,  //     Input (Const,Var,Abs)
            0x05, 0x01,        //     Usage Page (Generic Desktop Ctrls)
            0x09, 0x30,        //     Usage (X)
            0x09, 0x31,        //     Usage (Y)
            0x15, 0x00,        //     Logical Minimum (0)
            0x26, (byte)0xFF, 0x7F,  //     Logical Maximum (32767)
            0x35, 0x00,        //     Physical Minimum (0)  <- 添加物理范围
            0x46, (byte)0xFF, 0x7F,  //     Physical Maximum (32767)
            0x75, 0x10,        //     Report Size (16)
            (byte)0x95, 0x02,        //     Report Count (2)
            (byte)0x81, 0x02,  //     Input (Data,Var,Abs)
            (byte)0xC0,              //   End Collection
            (byte) 0xC0               // End Collection
    };

    public LightGunActivity() {
//        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==KeyEvent.KEYCODE_HEADSETHOOK){
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
//        Log.i(TAG, "called onCreate");
//        Log.d("HID","onCreate start");
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show();
            return;
        }

        // Load native library after(!) OpenCV initialization
        System.loadLibrary("mixed_sample");

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_lightgun);

        mThresholdSeekBar = findViewById(R.id.seek_threshold);

        mThresholdSeekBar.setMax(255);
        mThresholdSeekBar.setProgress(mThreshold);

        mThresholdSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        mThreshold = progress;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );

        mBtnShoot = findViewById(R.id.btn_shoot);

// 初始化 AudioRecord
        mAudioMonitor = new AudioMonitor(
                this,
                new AudioMonitor.Listener() {

                    @Override
                    public void onAudioData(
                            short[] waveform,
                            int length,
                            double rms,
                            double dbfs,
                            short peakPositive,
                            short peakNegative
                    ) {


                    }

                    @Override
                    public void onTriggerChanged(
                            boolean pressed
                    ) {

                        // AudioRecord线程不能直接操作UI，
                        // 所以切换到主线程。
                        runOnUiThread(() -> {

                            btn1_pressed = pressed;
                            mIsButtonPressed = pressed;

                            if (pressed) {

//                                Log.d(
//                                        "MIC_TRIGGER",
//                                        "TRIGGER DOWN"
//                                );

                            } else {

//                                Log.d(
//                                        "MIC_TRIGGER",
//                                        "TRIGGER UP"
//                                );
                            }

                            // 立即发送一次当前坐标和扳机状态
                            if (mTargetCoords[2] == 1.0f) {

                                int x =
                                        (int)mTargetCoords[0];

                                int y =
                                        (int)mTargetCoords[1];

                                x = Math.max(
                                        0,
                                        Math.min(1920, x)
                                );

                                y = Math.max(
                                        0,
                                        Math.min(1080, y)
                                );

                                sendGunPosition(
                                        x,
                                        y,
                                        pressed
                                );
                            }
                        });
                    }
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.RECORD_AUDIO
                        },
                        AUDIO_PERMISSION_REQUEST
                );

            } else {

                startAudioMonitor();
            }

        } else {

            startAudioMonitor();
        }

        // 设置按钮触摸监听
        mBtnShoot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 按钮按下 - 模拟鼠标左键按下
                        mIsButtonPressed = true;
                        btn1_pressed = true;
                        // 立即发送一次点击报告
                        if (mTargetCoords[2] == 1.0f) {
                            int x = (int)mTargetCoords[0];
                            int y = (int)mTargetCoords[1];
                            x = Math.max(0, Math.min(1920, x));
                            y = Math.max(0, Math.min(1080, y));
                            sendGunPosition(x, y, true);
                        }
//                        Log.d("HID", "Button DOWN - Trigger ON");
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 按钮释放 - 模拟鼠标左键释放
                        mIsButtonPressed = false;
                        btn1_pressed = false;
                        // 立即发送一次释放报告
                        if (mTargetCoords[2] == 1.0f) {
                            int x = (int)mTargetCoords[0];
                            int y = (int)mTargetCoords[1];
                            x = Math.max(0, Math.min(1920, x));
                            y = Math.max(0, Math.min(1080, y));
                            sendGunPosition(x, y, false);
                        }
//                        Log.d("HID", "Button UP - Trigger OFF");
                        return true;
                }
                return false;
            }
        });


        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        mOpenCvCameraView = findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        BluetoothAdapter adapter =
                BluetoothAdapter.getDefaultAdapter();


        adapter.getProfileProxy(
                this,
                new BluetoothProfile.ServiceListener() {

                    @Override
                    public void onServiceConnected(
                            int profile,
                            BluetoothProfile proxy) {


                        if(profile == BluetoothProfile.HID_DEVICE)
                        {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                mHidDevice =
                                        (BluetoothHidDevice)proxy;
                            }

//
//                            Log.d(
//                                    "HID",
//                                    "HID connected"
//                            );


                            registerHid();

                        }
                    }


                    @Override
                    public void onServiceDisconnected(
                            int profile) {

                        mHidDevice=null;

                    }

                },
                BluetoothProfile.HID_DEVICE
        );

    }

    private void startAudioMonitor() {

        if (mAudioMonitor == null) {
            return;
        }

        boolean result =
                mAudioMonitor.start();

//        Log.d(
//                "AUDIO",
//                "AudioMonitor start = " + result
//        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode ==
                AUDIO_PERMISSION_REQUEST) {

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

//                Log.d(
//                        "AUDIO",
//                        "RECORD_AUDIO permission granted"
//                );

                startAudioMonitor();

            } else {

                Log.e(
                        "AUDIO",
                        "RECORD_AUDIO permission denied"
                );
            }
        }
    }


    @Override
    public void onPause()
    {
        super.onPause();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        mHandler.removeCallbacks(mUpdateRunnable);

        if (mAudioMonitor != null) {
            mAudioMonitor.stop();
        }
    }
    private void registerHid()
    {

        if(mHidDevice==null)
            return;


        BluetoothHidDeviceAppSdpSettings sdp =
                null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sdp = new BluetoothHidDeviceAppSdpSettings(
                    "Android LightGun",
                    "LightGun Mouse",
                    "Android",
                    BluetoothHidDevice.SUBCLASS1_MOUSE,
                    LIGHTGUN_DESCRIPTOR
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                android.Manifest.permission.BLUETOOTH_CONNECT
                        },
                        100
                );

                return;
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mHidDevice.registerApp(
                    sdp,
                    null,
                    null,
                    Executors.newSingleThreadExecutor(),
                    new BluetoothHidDevice.Callback(){

                        @Override
                        public void onAppStatusChanged(
                                BluetoothDevice pluggedDevice,
                                boolean registered)
                        {

//                            Log.d(
//                                    "HID",
//                                    "registered="
//                                            +registered
//                                            +" device="
//                                            +pluggedDevice
//                            );
//
//                            Log.d("HID", "" + (pluggedDevice != null));
                            if(registered) {
                                if(pluggedDevice != null)
                                {
                                    mHostDevice = pluggedDevice;
                                }
                                else
                                {
//                                    Log.d(
//                                            "HID",
//                                            "registered success, start discoverable"
//                                    );


                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {

                                            Intent intent =
                                                    new Intent(
                                                            BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);


                                            intent.putExtra(
                                                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                                                    300
                                            );


                                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                            {
                                                if(checkSelfPermission(
                                                        Manifest.permission.BLUETOOTH_ADVERTISE)
                                                        != PackageManager.PERMISSION_GRANTED)
                                                {
                                                    requestPermissions(
                                                            new String[]{
                                                                    Manifest.permission.BLUETOOTH_ADVERTISE
                                                            },
                                                            100
                                                    );
                                                    return;
                                                }
                                            }
                                            startActivity(intent);

                                        }
                                    });

                                }
                            }

                        }


                        @Override
                        public void onConnectionStateChanged(
                                BluetoothDevice device,
                                int state)
                        {

//                            Log.d(
//                                    "HID",
//                                    "connection device="
//                                            +device
//                                            +" state="
//                                            +state
//                            );


                            if(state ==
                                    BluetoothProfile.STATE_CONNECTED)
                            {

                                mHostDevice=device;


//                                Log.d(
//                                        "HID",
//                                        "HOST CONNECTED "
//                                                +mHostDevice
//                                );
                            }


                            if(state ==
                                    BluetoothProfile.STATE_DISCONNECTED)
                            {

                                mHostDevice=null;

//                                Log.d(
//                                        "HID",
//                                        "HOST DISCONNECTED"
//                                );
                            }

                        }


                    }
            );
        }
    }
    @Override
    public void onResume()
    {
        super.onResume();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.enableView();

        new Handler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        startAutoFocus();
                    }
                },
                500
        );

        mHandler.postDelayed(
                mUpdateRunnable,
                UPDATE_INTERVAL
        );

// 重新启动 MIC
        if (mAudioMonitor != null) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                if (checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED) {

                    mAudioMonitor.start();
                }

            } else {

                // Android 6.0 以下不需要运行时申请权限
                mAudioMonitor.start();
            }
        }
    }

// 定时更新Runnable
private Runnable mUpdateRunnable = new Runnable() {
    @Override
    public void run() {
        // 如果找到了目标，更新鼠标位置
        if (mTargetCoords[2] == 1.0f) {
            // 坐标范围是0-1920, 0-1080
            int x = (int)mTargetCoords[0];
            int y = (int)mTargetCoords[1];

            // 限制范围防止溢出
            x = Math.max(0, Math.min(1920, x));
            y = Math.max(0, Math.min(1080, y));

            sendGunPosition(x, y, btn1_pressed);
        }

        // 继续下一帧
        mHandler.postDelayed(this, UPDATE_INTERVAL);
    }
};
    private void sendGunPosition(int x, int y, boolean trigger) {
        // 将屏幕坐标映射到 0-32767 范围
        int hidX = x * 32767 / 1920;
        int hidY = y * 32767 / 1080;

        // 限制范围
        hidX = Math.max(0, Math.min(32767, hidX));
        hidY = Math.max(0, Math.min(32767, hidY));

        byte buttons = 0;
        if(trigger) buttons |= 1;  // 左键 (和Arduino的MOUSE_LEFT对应)

        // 报告格式：按钮(1字节) + X(2字节) + Y(2字节) = 5字节
        byte[] report = new byte[5];
        report[0] = buttons;                    // 按钮状态
        report[1] = (byte)(hidX & 0xFF);        // X低8位
        report[2] = (byte)((hidX >> 8) & 0xFF); // X高8位
        report[3] = (byte)(hidY & 0xFF);        // Y低8位
        report[4] = (byte)((hidY >> 8) & 0xFF); // Y高8位 - 之前漏了这行！

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                    return;
                }
            }

            try {
                // 注意：Report ID必须为1，和描述符中的0x85 0x01匹配
                boolean result = mHidDevice.sendReport(mHostDevice, 1, report);
//                Log.d("HID", String.format("sendReport: result=%s, X=%d(0x%04X), Y=%d(0x%04X), btn=%d",
//                        result, hidX, hidX, hidY, hidY, buttons));
            } catch (SecurityException e) {
                Log.e("HID", "sendReport permission error", e);
            }
        }
    }


    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy()
    {
        if (mAudioMonitor != null) {
            mAudioMonitor.stop();
        }

        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }

        super.onDestroy();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

        mRgba = new Mat(
                height,
                width,
                CvType.CV_8UC4
        );

        mGray = new Mat(
                height,
                width,
                CvType.CV_8UC1
        );
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }
    private void startAutoFocus()
    {

        Camera camera =
                ((MyJavaCameraView)mOpenCvCameraView)
                        .getCamera();


        if(camera == null)
            return;


        Camera.Parameters params =
                camera.getParameters();


        params.setFocusMode(
                Camera.Parameters.FOCUS_MODE_AUTO);


        camera.setParameters(params);



        camera.autoFocus(
                new Camera.AutoFocusCallback()
                {

                    @Override
                    public void onAutoFocus(
                            boolean success,
                            Camera camera)
                    {

//                        Log.d(
//                                "AF",
//                                "auto focus result="
//                                        + success
//                        );


                        // 对焦完成
                        // 不再启动continuous AF


                    }

                });

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        FindFeatures(
                mGray.getNativeObjAddr(),
                mRgba.getNativeObjAddr(),
                mTargetCoords,
                mThreshold
        );

        return mRgba;
    }


    public native void FindFeatures(long matAddrGr, long matAddrRgba, float[] outputCoords,
                                    int thresholdValue);





}
