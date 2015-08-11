package isa.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Iterator;
import java.util.TreeMap;

import be.tarsos.dsp.pitch.PitchDetectionResult;

public class LightView extends View {

	private FFTConstants fftConts;

	private Paint paint = new Paint();
	private Paint paintAxis = new Paint();
	private int maxHz;
	private float maxPw = 0.0f;

	private double HzStep;
//	private int stepsLR = (int) (90 / HzStep); // 90Hz分のステップ数
	private int stepsLR;

	private double[] buf = new double[512];
	private boolean[] isPeak = new boolean[512];

	private TreeMap<Integer, Integer> gapTree = new TreeMap<Integer, Integer>();

	private double[] pw = new double[48];
	private double pwBorder = 0.0;

	private boolean[][] valAlive = new boolean[12][4];
	private int[][] gapPlot = new int[12][4];

	private float[][] _notePower = new float[12][4];
	private float _maxNotePower = 1.0f;

	private PitchDetectionResult[] _result;

	public boolean enableUpdate = true;

	// 音高データ
	private static final double[] d = { 110.00000000, 116.54094038,
			123.47082531, 130.81278265, 138.59131549, 146.83238396,
			155.56349186, 164.81377846, 174.61411572, 184.99721136,
			195.99771799, 207.65234879 };

	// 12色の色相環
	int[] col12 = new int[] { Color.rgb(255, 0, 0), Color.rgb(255, 127, 0),
			Color.rgb(255, 255, 0), Color.rgb(127, 255, 0),
			Color.rgb(0, 255, 0), Color.rgb(0, 255, 127),
			Color.rgb(0, 255, 255), Color.rgb(0, 127, 255),
			Color.rgb(0, 0, 255), Color.rgb(127, 0, 255),
			Color.rgb(255, 0, 255), Color.rgb(255, 0, 127) };

	// 音名
	final String[] s = { "A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#",
			"G", "G#" };

	// LightViewのコンストラクタ
	public LightView(Context context) {
		super(context);
        fftConts = new FFTConstants();

		paintAxis.setStrokeWidth(2.0f);
		paintAxis.setColor(Color.rgb(0, 0, 64));
		paintAxis.setTextSize(30);
		// 結果を受けるのは半分のサイズ
		buf = new double[fftConts.bufferSize / 2];
		isPeak = new boolean[fftConts.bufferSize / 2];

		// 刻み幅をセット
		HzStep = fftConts.hzStep;
		stepsLR = (int) (90 / HzStep);
	}

	public void setSpectrum(double[] buffer, PitchDetectionResult[] pdr) {
		if (!enableUpdate)
			return;
		// maxパワー
		_result = pdr.clone();
		double max = 0.0;
		float border;
		for (int i = 0; i < buf.length; i++) {
			buf[i] = buffer[2 * i] * buffer[2 * i] + buffer[2 * i + 1]
					* buffer[2 * i + 1];
			if (max < buf[i]) {
				max = buf[i];
				maxHz = i;
			}
			isPeak[i] = false;
		}

		// ピーク検出 - maxの1/10以上で左右より高く、
		// かつ左右50Hz幅に自分より高いものがない
		// 100Hz以下は検出しない
		maxPw = (float) max;
		border = (float) (max / 10);
		for (int i = (int) (100 / HzStep); i < buf.length; i++) {
			if (buf[i] > border) {
				int j = Math.max(0, i - stepsLR);
				int stp = Math.min(i + stepsLR + 1, buf.length);
				for (; j < stp; j++) {
					if (buf[j] > buf[i]) {
						isPeak[i] = false;
						break;
					}
				}
				if (j == stp) {
					isPeak[i] = true;
				}
			}
		}
		calcPower();
		invalidate();
	}

	private void calcPower() {
		double _HzWid = 5.0;
		_maxNotePower = 1.0f;
		// A2=110HzからC#6=1108Hzまで(だいたい)
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 12; j++) {
				_notePower[j][i] = 0.0f;
				for (int t = 1; d[j] * t * Math.pow(2, i) < fftConts.samplingRate / 2.0; t++) {
					double tagHzB = (d[j] * t * Math.pow(2, i));
					int _belId = (int) ((tagHzB - _HzWid) / HzStep);
					int _avoId = (int) ((tagHzB + _HzWid) / HzStep);
					for (int k = _belId; k < _avoId; k++) {
						_notePower[j][i] += buf[k];
					}
				}
				if (_notePower[j][i] > _maxNotePower)
					_maxNotePower = _notePower[j][i];
			}
		}
	}

	public int[] detectChord(double hz, boolean b) {
		int[] ret = new int[2];
		ret[0] = 2;
		// A2=110HzからC#6=1108Hzまで(だいたい)
		int t = 1; // オクターブ
		int i = 0;
		while (hz >= d[i] * t) {
			++i;
			if (i > 11) {
				i = 0;
				t *= 2;
				++ret[0];
			}
		}
		// hzがd[i-1]*tとd[i]*tの間にある
		double lesser, greater = d[i] * t;
		if (i == 0)
			lesser = d[11] * (t / 2);
		else
			lesser = d[i - 1] * t;
		// 上方修正
		if ((hz - lesser) * 7 <= (greater - hz) * 3) {
			if (i == 0)
				ret[1] = 11;
			else
				ret[1] = i - 1;
		} else
			ret[1] = i;

		// 表示用に補正するとき
		if (b && ret[1] > 2)
			++ret[0];

		return ret;
	}

	private double getPitchGap() {
		// 初期化
		gapTree.clear();
		int prev = 0;
		for (int i = 0; i < buf.length; i++) {
			if (isPeak[i]) {
				int tmp = i - prev;
				if (tmp * HzStep > 100) {
					boolean b = false;
					// 各peakは75Hz以上離れていることが保証されている
					for (Iterator<Integer> iter = gapTree.keySet().iterator(); iter
							.hasNext();) {
						Integer key = iter.next();
						Integer val = gapTree.get(key);
						// ±1stepに入っていたら
						if (!b && tmp <= (int) key + 0 && tmp >= (int) key - 0) {
							// まったく同じ値なら
							if (tmp == key)
								gapTree.put(key, val + 1);
							else {
								// gapTree.put(key, val+1);
								gapTree.put(tmp, 1);
							}
							b = true;
							break;
						}
					}
					// 新しいピーク間隔
					if (!b) {
						gapTree.put(tmp, 1);
					}
					prev = i;
				}
			}
		}

		int p = 0, ret = 0;
		for (Iterator<Integer> iter = gapTree.keySet().iterator(); iter
				.hasNext();) {
			Integer key = iter.next();
			Integer val = gapTree.get(key);
			if (val > p) {
				p = val;
				ret = key;
			}
		}

		return ret * HzStep;
	}

	private int[] getPitchGap2() {
		// 初期化
		gapTree.clear();
		int prev = 0;
		for (int i = 0; i < buf.length; i++) {
			if (isPeak[i]) {
				int tmp = i - prev;
				if (tmp * HzStep > 100) {
					boolean b = false;
					// 各peakは75Hz以上離れていることが保証されている
					for (Iterator<Integer> iter = gapTree.keySet().iterator(); iter
							.hasNext();) {
						Integer key = iter.next();
						Integer val = gapTree.get(key);
						// ±1stepに入っていたら
						if (!b && tmp <= (int) key + 0 && tmp >= (int) key - 0) {
							// まったく同じ値なら
							if (tmp == key)
								gapTree.put(key, val + 1);
							else {
								// gapTree.put(key, val+1);
								gapTree.put(tmp, 1);
							}
							b = true;
							break;
						}
					}
					// 新しいピーク間隔
					if (!b) {
						gapTree.put(tmp, 1);
					}
					prev = i;
				}
			}
		}

		// gapTreeの集計
		int[] gapPlot_pre = new int[50];
		// 値が生きているか
		valAlive = new boolean[12][4];
		for (Iterator<Integer> iter = gapTree.keySet().iterator(); iter
				.hasNext();) {
			Integer key = iter.next();
			Integer val = gapTree.get(key);
			int[] gpHz = detectChord(key * HzStep, false); // {2, 0}=(A2)から返る
			if (gpHz[0] <= 5) {
				gapPlot_pre[gpHz[1] + (gpHz[0] - 2) * 12 + 1] += val;
			}
		}

		gapPlot = new int[12][4];
		// 配列形式に直す
		// 隣り合う音は先に寄与させておく?
		for (int i = 0; i < gapPlot.length; i++) {
			for (int j = 0; j < gapPlot[0].length; j++) {
				// 自分自身
				gapPlot[i][j] = gapPlot_pre[i + 12 * j + 1];
				if (gapPlot[i][j] > 0)
					valAlive[i][j] = true;

				// 一つ高いほう
				if (gapPlot[i][j] > (int) (gapPlot_pre[i + 12 * j + 2] / 4.0)
						&& gapPlot_pre[i + 12 * j + 2] > 4) {
					gapPlot[i][j] += gapPlot_pre[i + 12 * j + 2] / 4;
					valAlive[i][j] = true;
				}
				// 一つ低いほう
				if (gapPlot[i][j] > (int) (gapPlot_pre[i + 12 * j] / 4.0)
						&& gapPlot_pre[i + 12 * j] > 4) {
					gapPlot[i][j] += gapPlot_pre[i + 12 * j] / 4;
					valAlive[i][j] = true;
				}

			}
		}

		// 倍音効果をまとめる
		for (int i = 0; i < gapPlot.length; i++) {
			for (int j = 0; j < gapPlot[0].length - 1; j++) {
				if (gapPlot[i][j] > (int) (gapPlot[i][j + 1] / 5)) { // 2倍音（1オクターブ上）
					gapPlot[i][j] += gapPlot[i][j + 1];
					valAlive[i][j + 1] = false;
				}
				if (gapPlot[i][j] > 0 && (j < 2 || i < 5)) { // 3倍音（1オクターブと7半音上）
					if (i < 5) {
						if (gapPlot[i][j] > (int) (gapPlot[i + 7][j + 1] / 5)) {
							gapPlot[i][j] += gapPlot[i + 7][j + 1];
							valAlive[i + 7][j + 1] = false;
						}
					} else {
						if (gapPlot[i][j] > (int) (gapPlot[i - 5][j + 2] / 5)) {
							gapPlot[i][j] += gapPlot[i - 5][j + 2];
							valAlive[i - 5][j + 2] = false;
						}
					}
				}
				if (j < 2) {
					if (gapPlot[i][j] > (int) (gapPlot[i][j + 2] / 5)) { // 4倍音（2オクターブ上）
						gapPlot[i][j] += gapPlot[i][j + 2];
						valAlive[i][j + 2] = false;
					}
				}
			}
		}

		// 改めてmaxを測る
		int p = 0, q = 0, max = 0;
		for (int i = 0; i < gapPlot.length; i++) {
			for (int j = 0; j < gapPlot[0].length; j++) {
				if (gapPlot[i][j] < 1)
					valAlive[i][j] = false;
				else if (valAlive[i][j] && gapPlot[i][j] > max) {
					max = gapPlot[i][j];
					p = i;
					q = j + 2;
				}
			}
		}
		if (p > 2)
			q++;
		return new int[] { q, p };
	}

	// 画面の描画
	@Override
	protected void onDraw(Canvas canvas) {
		float _xAxisY = this.getHeight() - 50.0f, _yAxisX = 20.0f;
		float _topSpace = this.getHeight() * 0.4f;

		canvas.drawColor(Color.WHITE);

		// ZOOM
		double zoomX = 9.6, zoomY = (_xAxisY - _topSpace) / maxPw;
		paint.setTextSize(20);

		// 周波数スペクトルの表示
		paint.setColor(Color.BLACK);
		for (int i = 0; i < buf.length; i++) {
            canvas.drawLine((float) (i * zoomX + _yAxisX), -(float) (buf[i] * zoomY) + _xAxisY,
                    (float) (i * zoomX + _yAxisX), _xAxisY, paint);
//			canvas.drawLine((float) (i * zoomX + _yAxisX),
//					-(float) (buf[i] * zoomY) + _xAxisY, (float) ((i + 1)
//							* zoomX + _yAxisX), -(float) (buf[i + 1] * zoomY)
//							+ _xAxisY, paint);
			if (isPeak[i]) {
				paint.setColor(Color.RED);
				canvas.drawLine((float) (i * zoomX + _yAxisX), _topSpace,
						(float) (i * zoomX + _yAxisX), _xAxisY, paint);
				// canvas.drawText((int)(i*HzStep)+"", (float)(i*zoomX+yAxis),
				// 205, paint);
				paint.setColor(Color.BLACK);
			}
		}
		// draw x axis
		canvas.drawLine(20.0f, _xAxisY, this.getWidth(), _xAxisY, paintAxis);
		canvas.drawText("[Hz]", this.getWidth() - 55.0f, _xAxisY - 20.0f,
				paintAxis);
		// draw digits
		for (int p = 0; p <= 6000; p += 250) {
			if (p % 500 == 0) {
				canvas.drawLine((float) (p / HzStep * zoomX + _yAxisX),
						_xAxisY, (float) (p / HzStep * zoomX + _yAxisX),
						_xAxisY + 20.0f, paintAxis);
				if (p % 1000 == 0) {
					if (p == 0)
						canvas.drawText(p + "", (float) (p / HzStep * zoomX
								+ _yAxisX - 9.0f), _xAxisY + 40.0f, paintAxis);
					else
						canvas.drawText(p + "", (float) (p / HzStep * zoomX
								+ _yAxisX - 32.0f), _xAxisY + 40.0f, paintAxis);
				}
			} else
				canvas.drawLine((float) (p / HzStep * zoomX + _yAxisX),
						_xAxisY, (float) (p / HzStep * zoomX + _yAxisX),
						_xAxisY + 10.0f, paintAxis);
		}
		if (_result != null) {
			String[] _tit = new String[] { "Wave", "McLo", "FaYi", "FFT ",
					"YIN " };
			for (int i = 0; i < 5; i++) {
				if (_result[i] != null) {
					float p = _result[i].getPitch();
					canvas.drawText(_tit[i] + " " + p + "Hz", 400,
							360 + 20 * i, paintAxis);
				}
			}
		}
		// draw y axis
		canvas.drawLine(_yAxisX, _topSpace, _yAxisX, _xAxisY, paintAxis);

		double curHzG = getPitchGap();
		int[] ret = detectChord(curHzG, true);

		// draw max likelyhood
		float _rectSize = (_topSpace - 35.0f) / 4.0f;
		paint.setTextSize(_rectSize * 0.4f);
		double _dis = _rectSize / Math.sqrt(2);
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 12; j++) {
				float _begX = 10.0f + j * (_rectSize + 5.0f), _begY = 10.0f + i
						* (_rectSize + 5.0f), _intX = 10.0f + _rectSize * 0.5f
						+ j * (_rectSize + 5.0f), _intY = 10.0f + _rectSize
						* 0.5f + i * (_rectSize + 5.0f);
				double _ratio = Math.sqrt(_notePower[j][i] / _maxNotePower)
						/ Math.sqrt(2);
				paint.setColor(col12[j]);
				paint.setAlpha(128);
				canvas.drawRect(_intX - (float) (_dis * _ratio), _intY
						- (float) (_dis * _ratio), _intX
						+ (float) (_dis * _ratio), _intY
						+ (float) (_dis * _ratio), paint);
				paint.setARGB(255, 0, 0, 0);
				int _oct = i + 3;
				if (j < 3)
					_oct--;
				canvas.drawText(s[j] + _oct, _begX, _begY + _rectSize, paint);
			}
		}
	}
}
