package isa.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Log;

/**
 * Created by Takahito on 15/08/12.
 */
public class FFTConstants {

    // エミュレータ = true
    protected boolean isDebug = false;

    // サンプリングレート
    protected int samplingRate;
    // データ長
    protected int bufferSize = 1024;
    // 周波数刻み幅 = サンプリングレート / データ長
    protected double hzStep;

    protected FFTConstants() {
        _init();
    }

    protected FFTConstants(boolean _isDebug) {
        isDebug = _isDebug;
        _init();
    }

    private void _init() {
        samplingRate = 8000;
        if (!isDebug)
            samplingRate = 44100;

        int tmp = AudioRecord.getMinBufferSize(samplingRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        // 2のべき乗に整える
        while (bufferSize < tmp)
            bufferSize *= 2;
        // エミュレータでないならダメ押し2倍
//        if (!isDebug)
//            bufferSize *= 2;

        hzStep = samplingRate / (double)bufferSize;
        Log.e("HzStep", hzStep+"Hz");

    }


}
