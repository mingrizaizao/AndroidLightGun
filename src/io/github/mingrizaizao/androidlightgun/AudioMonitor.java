package io.github.mingrizaizao.androidlightgun;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioMonitor {

    public interface Listener {

        // 音频数据，用于波形显示
        void onAudioData(
                short[] waveform,
                int length,
                double rms,
                double dbfs,
                short peakPositive,
                short peakNegative
        );

        // 扳机状态发生变化
        void onTriggerChanged(boolean pressed);
    }

    private final Context context;
    private final Listener listener;

    private AudioRecord audioRecord;
    private Thread audioThread;

    private volatile boolean running = false;

    // 当前扳机状态
    private boolean triggerPressed = false;

    // 你的实际测试结果：
    //
    // 正常：
    // Peak+ ≈ +200
    // Peak- ≈ -200
    //
    // 按下：
    // Peak+ ≈ +9000
    //
    // 抬起：
    // Peak- ≈ -9000
    //
    // 所以先使用3000作为阈值。
    private static final int TRIGGER_THRESHOLD = 3000;

    // 采样率
    private static final int SAMPLE_RATE = 16000;

    // 每次读取512个采样点
    // 512 / 16000 = 32ms
    private static final int BUFFER_SIZE = 512;

    public AudioMonitor(
            Context context,
            Listener listener
    ) {

        this.context =
                context.getApplicationContext();

        this.listener = listener;
    }

    public boolean start() {

        if (running) {
            return true;
        }

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.M) {

            if (context.checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                return false;
            }
        }

        int minBufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

        if (minBufferSize ==
                AudioRecord.ERROR ||
                minBufferSize ==
                        AudioRecord.ERROR_BAD_VALUE) {

            return false;
        }

        int bufferSize =
                Math.max(
                        minBufferSize,
                        BUFFER_SIZE * 2
                );

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        if (audioRecord.getState() !=
                AudioRecord.STATE_INITIALIZED) {

            audioRecord.release();
            audioRecord = null;

            return false;
        }

        running = true;

        audioThread = new Thread(
                () -> {

                    short[] buffer =
                            new short[BUFFER_SIZE];

                    try {

                        audioRecord.startRecording();

                        while (running) {

                            int count =
                                    audioRecord.read(
                                            buffer,
                                            0,
                                            buffer.length
                                    );

                            if (count <= 0) {
                                continue;
                            }

                            processAudio(
                                    buffer,
                                    count
                            );
                        }

                    } catch (Exception e) {

                        e.printStackTrace();

                    } finally {

                        try {

                            if (audioRecord != null) {
                                audioRecord.stop();
                            }

                        } catch (Exception ignored) {
                        }
                    }

                },
                "AudioMonitorThread"
        );

        audioThread.start();

        return true;
    }

    private void processAudio(
            short[] buffer,
            int count
    ) {

        long sumSquares = 0;

        short peakPositive = 0;
        short peakNegative = 0;

        for (int i = 0; i < count; i++) {

            short sample = buffer[i];

            // RMS
            sumSquares +=
                    (long) sample * sample;

            // 正峰值
            if (sample > peakPositive) {
                peakPositive = sample;
            }

            // 负峰值
            if (sample < peakNegative) {
                peakNegative = sample;
            }
        }

        double rms =
                Math.sqrt(
                        (double) sumSquares / count
                );

        double dbfs;

        if (rms > 0) {

            dbfs =
                    20.0 *
                            Math.log10(
                                    rms / 32768.0
                            );

        } else {

            dbfs = -100.0;
        }

        // ====================================================
        // 扳机检测
        // ====================================================

        // 按下：
        //
        // Peak+ > 3000
        //
        // 而且当前必须不是DOWN
        //
        if (!triggerPressed &&
                peakPositive >
                        TRIGGER_THRESHOLD) {

            triggerPressed = true;

            if (listener != null) {

                listener.onTriggerChanged(true);
            }
        }

        // 抬起：
        //
        // Peak- < -3000
        //
        // 而且当前必须是DOWN
        //
        if (triggerPressed &&
                peakNegative <
                        -TRIGGER_THRESHOLD) {

            triggerPressed = false;

            if (listener != null) {

                listener.onTriggerChanged(false);
            }
        }

        // ====================================================
        // 发送波形调试数据
        // ====================================================

        if (listener != null) {

            short[] waveform =
                    new short[count];

            System.arraycopy(
                    buffer,
                    0,
                    waveform,
                    0,
                    count
            );

            listener.onAudioData(
                    waveform,
                    count,
                    rms,
                    dbfs,
                    peakPositive,
                    peakNegative
            );
        }
    }

    public void stop() {

        running = false;

        if (audioThread != null) {

            try {

                audioThread.join(500);

            } catch (InterruptedException ignored) {
            }

            audioThread = null;
        }

        if (audioRecord != null) {

            try {
                audioRecord.release();
            } catch (Exception ignored) {
            }

            audioRecord = null;
        }

        triggerPressed = false;
    }

    public boolean isRunning() {
        return running;
    }
}