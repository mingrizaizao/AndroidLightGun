#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>
#include <android/log.h>

using namespace std;
using namespace cv;

#define LOG_TAG "LightGun"

#define LOGD(...) __android_log_print(  \
ANDROID_LOG_DEBUG,                      \
LOG_TAG,                                \
__VA_ARGS__)

extern "C" {

vector<Point> sortCorners(vector<Point> pts)
{
    vector<Point> result(4);
    // 左上
    result[0]=pts[0];
    // 找中心
    Point center(0,0);
    for(auto&p:pts)
    {
        center.x += p.x;
        center.y += p.y;
    }
    center.x/=4;
    center.y/=4;
    for(auto&p:pts)
    {
        if(p.x<center.x && p.y<center.y)
        {
            result[0]=p;
        }
        else if(p.x>center.x && p.y<center.y)
        {
            result[1]=p;
        }
        else if(p.x>center.x && p.y>center.y)
        {
            result[2]=p;
        }
        else
        {
            result[3]=p;
        }
    }
    return result;
}


JNIEXPORT void JNICALL Java_io_github_mingrizaizao_androidlightgun_LightGunActivity_FindFeatures(JNIEnv*, jobject, jlong addrGray, jlong addrRgba, jfloatArray outputCoords,
                                                                                        jint thresholdValue);
// 修改FindFeatures函数，增加输出参数
JNIEXPORT void JNICALL Java_io_github_mingrizaizao_androidlightgun_LightGunActivity_FindFeatures(
        JNIEnv* env,
        jobject thiz,
        jlong addrGray,
        jlong addrRgba,
        jfloatArray outputCoords,
        jint thresholdValue)
{

    bool foundTarget = false;

    Mat& gray = *(Mat*)addrGray;
    Mat& rgba = *(Mat*)addrRgba;

    Point2f gun(
            gray.cols/2,
            gray.rows/2
    );

    float targetX = gray.cols / 2.0f;
    float targetY = gray.rows / 2.0f;

    // 1. 二值化
    Mat binary;

    threshold(
            gray,
            binary,
            thresholdValue,
            255,
            THRESH_BINARY
    );

    // 将二值图显示到屏幕
    cvtColor(
            binary,
            rgba,
            COLOR_GRAY2RGBA
    );

    // 2. 找轮廓
    vector<vector<Point>> contours;
    findContours(
            binary,
            contours,
            RETR_EXTERNAL,
            CHAIN_APPROX_SIMPLE
    );

    // 3. 遍历轮廓
    for(auto& contour : contours)
    {
        double area = contourArea(contour);
        if(area < 10000)
            continue;

        // 多边形逼近
        vector<Point> approx;
        approxPolyDP(
                contour,
                approx,
                arcLength(contour,true)*0.02,
                true
        );

        // 判断是不是四边形
        if(approx.size()==4)
        {
            vector<Point> corners = sortCorners(approx);
            vector<Point2f> src;
            for(auto&p:corners)
            {
                src.push_back(Point2f(p.x,p.y));
            }
            vector<Point2f> dst=
                    {
                            Point2f(0,0),
                            Point2f(1920,0),
                            Point2f(1920,1080),
                            Point2f(0,1080)
                    };
            Mat H=getPerspectiveTransform(
                    src,
                    dst
            );
            vector<Point2f> input;
            input.push_back(gun);
            vector<Point2f> output;
            perspectiveTransform(
                    input,
                    output,
                    H
            );
            if (output.size() > 0){
                foundTarget = true;

            } else {

            }

            polylines(
                    rgba,
                    corners,
                    true,
                    Scalar(255,0,0,255),
                    5
            );

            if (output.size() > 0) {
                targetX = output[0].x;
                targetY = output[0].y;
                foundTarget = true;
            }

        }
    }

    Scalar crosshairColor(0, 255, 0, 255);

// 狙击镜外圈
    circle(
            rgba,
            gun,
            45,
            crosshairColor,
            3
    );

// 中心点
    circle(
            rgba,
            gun,
            4,
            crosshairColor,
            FILLED
    );

// 上
    line(
            rgba,
            Point(gun.x, gun.y - 45),
            Point(gun.x, gun.y - 110),
            crosshairColor,
            4
    );

// 下
    line(
            rgba,
            Point(gun.x, gun.y + 45),
            Point(gun.x, gun.y + 110),
            crosshairColor,
            4
    );

// 左
    line(
            rgba,
            Point(gun.x - 45, gun.y),
            Point(gun.x - 110, gun.y),
            crosshairColor,
            4
    );

// 右
    line(
            rgba,
            Point(gun.x + 45, gun.y),
            Point(gun.x + 110, gun.y),
            crosshairColor,
            4
    );

    // 将坐标返回给Java
    if (outputCoords != nullptr) {
        jfloat* coords = env->GetFloatArrayElements(outputCoords, nullptr);
        if (coords != nullptr) {
            coords[0] = targetX;
            coords[1] = targetY;
            coords[2] = foundTarget ? 1.0f : 0.0f;  // 是否找到目标
            env->ReleaseFloatArrayElements(outputCoords, coords, 0);
        }
    }
}

}
