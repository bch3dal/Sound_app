package isa.android;

import android.media.*;
import be.tarsos.dsp.pitch.*;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class SoundSwitch implements Runnable {

	// ボリューム感知リスナー
	private OnReachedVolumeListener mListener;
    private FFTConstants fftConts;


	// 録音中フラグ
	private boolean isRecording = true;

	// ボーダー音量 & 最大音量減衰
	private short mBorderVolume = 500;
	private double maxDec = 0.85;

	private boolean continuous = true;

	private DynamicWavelet _diwDetector;
	private FFTPitch _fftDetector;
	private FastYin _fastYinDetector;
	private McLeodPitchMethod _mclDetector;
	private Yin _yinDetector;

	public PitchDetectionResult[] pdr = new PitchDetectionResult[5];

	// ボーダー音量をセット
	public void setBorderVolume(short volume) {
		mBorderVolume = volume;
	}

	// ボーダー音量を取得
	public short getBorderVolume() {
		return mBorderVolume;
	}

	// ボーダー音量をセット
	public void setMaxDec(int v) {
		maxDec = v / (double) 100;
	}

	// ボーダー音量を取得
	public int getMaxDec() {
		return (int) (maxDec * 100);
	}

	// 録音を停止
	public void stop() {
		isRecording = false;
	}

	// OnReachedVolumeListenerをセット
	public void setOnVolumeReachedListener(OnReachedVolumeListener listener) {
		mListener = listener;
	}

	// ボーダー音量を検知した時のためのリスナー
	public interface OnReachedVolumeListener {
		void onReachedVolume(short volume);
	}

	public int hz;
	public short[] buffer = new short[0];
	public double[] Dbuffer = new double[0];

	private short prevMax = 0;

	// private short[] prevMax = new short[]{0, 0, 0};

	public void init() {
        fftConts = new FFTConstants();

        int bufferSize = fftConts.bufferSize;
		buffer = new short[bufferSize];
		Dbuffer = new double[bufferSize];
		_fftDetector = new FFTPitch(fftConts.samplingRate, bufferSize);
		_mclDetector = new McLeodPitchMethod(fftConts.samplingRate, bufferSize);
		_yinDetector = new Yin(fftConts.samplingRate, bufferSize);
		_fastYinDetector = new FastYin(fftConts.samplingRate, bufferSize);
		_diwDetector = new DynamicWavelet(fftConts.samplingRate, bufferSize);
	}

	public boolean isRecording() {
		return isRecording;
	}

	private float[] _toFloat(short[] s) {
		float[] ret = new float[s.length];
		for (int i = 0; i < s.length; i++) {
			ret[i] = (float) s[i] / 1.0f;
		}
		return ret;
	}

	private double[] _toDouble(short[] s) {
		double[] ret = new double[s.length];
		for (int i = 0; i < s.length; i++)
			ret[i] = (double) s[i] / 32.0;
		return ret;
	}

	@Override
	public void run() {
		android.os.Process
				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		AudioRecord audioRecord = new AudioRecord(
				MediaRecorder.AudioSource.MIC, fftConts.samplingRate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
				fftConts.bufferSize);

		audioRecord.startRecording();
		int cooldown = 0;
		while (isRecording) {
			audioRecord.read(buffer, 0, fftConts.bufferSize);
			// おまじない
			// audioRecord.read(buffer, 0, bufferSize / 4);

			// とりあえずdoubleで処理
			Dbuffer = _toDouble(buffer);
			DoubleFFT_1D dFft = new DoubleFFT_1D(fftConts.bufferSize);
			dFft.realForward(Dbuffer);
			pdr[0] = _diwDetector.getPitch(_toFloat(buffer));
//			pdr[1] = _mclDetector.getPitch(_toFloat(buffer));
			pdr[2] = _fastYinDetector.getPitch(_toFloat(buffer));
			pdr[3] = _fftDetector.getPitch(_toFloat(buffer));
//			pdr[4] = _yinDetector.getPitch(_toFloat(buffer));

			hz = 0;
			short max = 0;
			for (int i = 0; i < fftConts.bufferSize; i++) {
				// 最大音量を計算
				max = (short) Math.max(max, buffer[i]);
			}

			/* 新バージョン */
			/*
			 * prevMax[0] = max; int count = 0; for (int i = 0; i <
			 * prevMax.length - 1; i++) { if (prevMax[1] <=
			 * prevMax[(i+2)%prevMax.length] * 1.01) count++; }
			 *
			 * //前回の最大音量がボーダーを超えていて、今回と前々回の結果をある程度上回るなら if (prevMax[1] >
			 * mBorderVolume && count < 1) { if (mListener != null)
			 * mListener.onReachedVolume(prevMax[1]); } else { if (mListener !=
			 * null) ;//mListener.onReachedVolume(prevMax[1], false); } for (int
			 * i = prevMax.length - 1; i > 0; i--) prevMax[i] = prevMax[i - 1];
			 * //前回の結果を記録 prevDbuffer = Dbuffer.clone();
			 */
			/* 前のバージョン */
			// 最大音量がボーダーを超えていたら
			if (max > mBorderVolume && (max > prevMax || continuous)) {
				if (mListener != null) {
					// リスナーを実行
					mListener.onReachedVolume(max);
				}
				prevMax = max;
			} else {
				prevMax = (short) (prevMax * maxDec);
				++cooldown;
				if (cooldown >= 10) {
					prevMax = 0;
					cooldown = 0;
				}
			}
		}
		audioRecord.stop();
		audioRecord.release();
	}
}
