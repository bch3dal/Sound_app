package isa.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {

	private SoundSwitch mSoundSwitch;
	private LightView mLightView;
	private Handler mHandler = new Handler();
	private LayoutInflater _lInflater;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mLightView = new LightView(this);
		setContentView(mLightView);
		_lInflater = LayoutInflater.from(this);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSoundSwitch = new SoundSwitch();
		mSoundSwitch.init();
		// リスナーを登録して音を感知できるように
		mSoundSwitch
				.setOnVolumeReachedListener(new SoundSwitch.OnReachedVolumeListener() {
					// 音を感知したら呼び出される
					public void onReachedVolume(short volume) {
						// 別スレッドからUIスレッドに要求/Handler.postでエラー回避
						mHandler.post(new Runnable() {
							// Runnableに入った要求を順番にLoopでrunを呼び出し処理
							public void run() {
								mLightView.setSpectrum(mSoundSwitch.Dbuffer, mSoundSwitch.pdr);
							}
						});
					}
				});
		// 別スレッドとしてSoundSwitchを開始（録音を開始）
		new Thread(mSoundSwitch).start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSoundSwitch.stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_top, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.menu_volBorder:
				final View _inputView = _lInflater.inflate(R.layout.setting_vol, null);
				final SeekBar _seekBar1,
				_seekBar2;
				final TextView _tv1,
				_tv2;
				if (mSoundSwitch != null) {
					_seekBar1 = (SeekBar) _inputView.findViewById(R.id.setVolBar);
					_seekBar1.setProgress(mSoundSwitch.getBorderVolume());
					_tv1 = (TextView) _inputView.findViewById(R.id.showVolValue);
					_tv1.setText("現在の値:" + _seekBar1.getProgress());
					_seekBar1
							.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

								@Override
								public void onStopTrackingTouch(SeekBar seekBar) {
								}

								@Override
								public void onStartTrackingTouch(SeekBar seekBar) {
								}

								@Override
								public void onProgressChanged(SeekBar seekBar,
										int progress, boolean fromUser) {
									if (fromUser == true) {
										if (progress < 500) {
											seekBar.setProgress(500);
										}
										_tv1.setText("現在の値:" + seekBar.getProgress());
									}
								}
							});

					_seekBar2 = (SeekBar) _inputView.findViewById(R.id.setMaxDec);
					_seekBar2.setProgress(mSoundSwitch.getMaxDec());
					_tv2 = (TextView) _inputView.findViewById(R.id.showMaxDec);
					_tv2.setText("現在の値: x" + (_seekBar2.getProgress() / 100.0));
					_seekBar2
							.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
								@Override
								public void onStopTrackingTouch(SeekBar seekBar) {
								}

								@Override
								public void onStartTrackingTouch(SeekBar seekBar) {
								}

								@Override
								public void onProgressChanged(SeekBar seekBar,
										int progress, boolean fromUser) {
									if (fromUser == true)
										_tv2.setText("現在の値: x"
												+ (seekBar.getProgress() / 100.0));
								}
							});
					AlertDialog.Builder setVol = new AlertDialog.Builder(this);
					setVol.setTitle("マイク閾値の設定").setCancelable(false)
							.setView(_inputView);
					setVol.setNegativeButton("cancel",
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
								}
							}).setPositiveButton("OK",
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
									mSoundSwitch.setBorderVolume((short) _seekBar1
											.getProgress());
									mSoundSwitch.setMaxDec(_seekBar2.getProgress());
								}
							});
					setVol.show();
				}
				break;
			case R.id.menu_keep:
				mLightView.enableUpdate = !mLightView.enableUpdate;
				break;
			default:
		}
		return true;
	}

	public void onPrevMaxReset() {
		Toast.makeText(this, "prevMax reset!", Toast.LENGTH_SHORT).show();
	}
}