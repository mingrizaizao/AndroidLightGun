package io.github.mingrizaizao.androidlightgun;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

public class MyJavaCameraView extends JavaCameraView {

    public MyJavaCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Camera getCamera() {
        return mCamera;
    }
}