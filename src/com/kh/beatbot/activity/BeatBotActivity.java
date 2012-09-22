package com.kh.beatbot.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.kh.beatbot.R;
import com.kh.beatbot.global.GlobalVars;
import com.kh.beatbot.global.Track;
import com.kh.beatbot.listener.MidiTrackControlListener;
import com.kh.beatbot.manager.Managers;
import com.kh.beatbot.manager.MidiManager;
import com.kh.beatbot.manager.PlaybackManager;
import com.kh.beatbot.manager.RecordManager;
import com.kh.beatbot.view.MidiView;
import com.kh.beatbot.view.SurfaceViewBase;
import com.kh.beatbot.view.ThresholdBarView;
import com.kh.beatbot.view.helper.LevelsViewHelper;
import com.kh.beatbot.view.helper.MidiTrackControlHelper;

public class BeatBotActivity extends Activity implements MidiTrackControlListener {
	private class FadeListener implements AnimationListener {
		boolean fadeOut;

		public void setFadeOut(boolean flag) {
			fadeOut = flag;
		}

		@Override
		public void onAnimationEnd(Animation animation) {
			if (fadeOut) {
				levelsGroup.setVisibility(View.GONE);
			}
		}

		@Override
		public void onAnimationRepeat(Animation animation) {
		}

		@Override
		public void onAnimationStart(Animation animation) {
			levelsGroup.setVisibility(View.VISIBLE);
		}
	}

	private Animation fadeIn, fadeOut;
	// these are used as variables for convenience, since they are reference
	// frequently
	private ToggleButton volume, pan, pitch;
	private ViewGroup levelsGroup;
	private FadeListener fadeListener;
	private static AssetManager assetManager;

	private static AlertDialog selectInstrumentAlert = null;

	private long lastTapTime = 0;

	private void initAndroidSettings() {
		// remove title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// assign hardware (ringer) volume +/- to media while this application
		// has focus
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	private void initLevelsIconGroup() {
		levelsGroup = (ViewGroup) findViewById(R.id.levelsLayout);
		volume = (ToggleButton) findViewById(R.id.volumeButton);
		pan = (ToggleButton) findViewById(R.id.panButton);
		pitch = (ToggleButton) findViewById(R.id.pitchButton);
		// fade animation for levels icons,
		// to match levels view is fading in/out in midiView
		fadeListener = new FadeListener();
		fadeIn = AnimationUtils.loadAnimation(this, R.anim.fadein);
		fadeOut = AnimationUtils.loadAnimation(this, R.anim.fadeout);
		fadeIn.setAnimationListener(fadeListener);
		fadeOut.setAnimationListener(fadeListener);
	}

	/**
	 * The Select Instrument Alert is shown when adding a new track
	 */
	private void initSelectInstrumentAlert() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Choose Instrument");
		builder.setItems(GlobalVars.currentInstruments.toArray(new String[GlobalVars.currentInstruments.size()]),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						addTrack(item);
					}
				});
		selectInstrumentAlert = builder.create();
	}

	private void initManagers(Bundle savedInstanceState) {
		Managers.init(savedInstanceState);
		Managers.midiManager.setActivity(this);
		setDeleteIconEnabled(false);
		((ThresholdBarView) findViewById(R.id.thresholdBar))
				.addLevelListener(Managers.recordManager);
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		in.close();
		in = null;
		out.flush();
		out.close();
		out = null;
	}

	private void copyFromAssetsToExternal(String folderName) {
		String newDirectory = GlobalVars.appDirectory + folderName + "/";
		File newSampleFolder = new File(newDirectory);
		newSampleFolder.mkdir();
		String[] filesToCopy = null;
		String[] existingFiles = newSampleFolder.list();
		try {
			filesToCopy = assetManager.list(folderName);
			for (String file : filesToCopy) {
				if (!Arrays.asList(existingFiles).contains(file)) {
					InputStream in = assetManager.open(folderName + "/" + file);
					OutputStream out = new FileOutputStream(newDirectory + file);
					copyFile(in, out);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void copyAllSamplesToStorage() {
		assetManager = getAssets();
		String extStorageDir = Environment.getExternalStorageDirectory()
				.toString();
		GlobalVars.appDirectory = extStorageDir + "/BeatBot/";
		File appDirectoryFile = new File(GlobalVars.appDirectory);
		// build the directory structure, if needed
		appDirectoryFile.mkdirs();
		for (String instrumentName : GlobalVars.currentInstruments) {
			// the sample folder for this sample type does not yet exist.
			// create it and write all assets of this type to the folder
			copyFromAssetsToExternal(instrumentName);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initAndroidSettings();
		copyAllSamplesToStorage();
		SurfaceViewBase.setResources(getResources());
		setContentView(R.layout.main);
		initLevelsIconGroup();
		initSelectInstrumentAlert();
		GlobalVars.initTracks();
		// recorded type
		if (savedInstanceState == null) {
			initNativeAudio();
		}
		GlobalVars.font = Typeface.createFromAsset(getAssets(),
				"REDRING-1969-v03.ttf");
		((TextView) findViewById(R.id.thresholdLabel))
				.setTypeface(GlobalVars.font);
		initManagers(savedInstanceState);
		GlobalVars.midiView = ((MidiView) findViewById(R.id.midiView));
		GlobalVars.midiView.initMeFirst();
		MidiTrackControlHelper.addListener(this);
		if (savedInstanceState != null) {
			GlobalVars.midiView.readFromBundle(savedInstanceState);
			// if going to levels view or in levels view, level icons should be
			// visible
			int levelsVisibilityState = GlobalVars.midiView.getViewState() == MidiView.State.TO_LEVELS_VIEW
					|| GlobalVars.midiView.getViewState() == MidiView.State.LEVELS_VIEW ? View.VISIBLE
					: View.GONE;
			levelsGroup.setVisibility(levelsVisibilityState);

		}

		// were we recording and/or playing before losing the instance?
		if (savedInstanceState != null) {
			if (savedInstanceState.getBoolean("recording")) {
				record(findViewById(R.id.recordButton));
			} else if (savedInstanceState.getBoolean("playing")) {
				play(findViewById(R.id.playButton));
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (isFinishing()) {
			Managers.recordManager.release();
			shutdown();
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable("midiManager", Managers.midiManager);
		GlobalVars.midiView.writeToBundle(outState);
		outState.putBoolean(
				"playing",
				Managers.playbackManager.getState() == PlaybackManager.State.PLAYING);
		outState.putBoolean("recording",
				RecordManager.getState() != RecordManager.State.INITIALIZING);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		Intent midiFileMenuIntent = new Intent(this, MidiFileMenuActivity.class);
		menu.findItem(R.id.midi_menu_item).setIntent(midiFileMenuIntent);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.snap:
			if (GlobalVars.midiView.toggleSnapToGrid())
				item.setIcon(R.drawable.btn_check_buttonless_on);
			else
				item.setIcon(R.drawable.btn_check_buttonless_off);
			return true;
		case R.id.quantize_current:
			Managers.midiManager.quantize(GlobalVars.currBeatDivision);
			return true;
		case R.id.quantize_quarter:
			Managers.midiManager.quantize(1);
		case R.id.quantize_eighth:
			Managers.midiManager.quantize(2);
			return true;
		case R.id.quantize_sixteenth:
			Managers.midiManager.quantize(4);
			return true;
		case R.id.quantize_thirty_second:
			Managers.midiManager.quantize(8);
			return true;
		case R.id.save_wav:
			return true;
			// midi import/export menu item is handled as an intent -
			// MidiFileMenuActivity.class
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void initNativeAudio() {
		createEngine();
		createAudioPlayer();
		for (int trackNum = 0; trackNum < GlobalVars.tracks.size(); trackNum++) {
			addTrack(GlobalVars.tracks.get(trackNum).getSampleBytes(0));
		}
	}

	public void setDeleteIconEnabled(final boolean enabled) {
		// need to change UI stuff on the UI thread.
		Handler refresh = new Handler(Looper.getMainLooper());
		refresh.post(new Runnable() {
			public void run() {
				((ImageButton) findViewById(R.id.delete)).setEnabled(enabled);
			}
		});
	}

	/**
	 * Open new intent for sample edit view, passing the track number to the
	 * intent as an extra.
	 */
	private void launchSampleEditActivity(int trackNum) {
		Intent intent = new Intent();
		intent.setClass(this, SampleEditActivity.class);
		intent.putExtra("trackNum", trackNum);
		startActivity(intent);
	}

	public void record(View view) {
		if (RecordManager.getState() != RecordManager.State.INITIALIZING) {
			Managers.recordManager.stopListening();
			((ToggleButton) view).setChecked(false);
		} else {
			GlobalVars.midiView.reset();
			// if we're already playing, Managers.midiManager is already ticking
			// away.
			if (Managers.playbackManager.getState() != PlaybackManager.State.PLAYING)
				play(findViewById(R.id.playButton));
			Managers.recordManager.startListening();
		}
	}

	public void play(View view) {
		((ToggleButton) view).setChecked(true);
		if (Managers.playbackManager.getState() == PlaybackManager.State.PLAYING) {
			Managers.midiManager.reset();
		} else if (Managers.playbackManager.getState() == PlaybackManager.State.STOPPED) {
			Managers.playbackManager.play();
		}
	}

	public void stop(View view) {
		if (RecordManager.getState() != RecordManager.State.INITIALIZING)
			record(findViewById(R.id.recordButton));
		if (Managers.playbackManager.getState() == PlaybackManager.State.PLAYING) {
			Managers.playbackManager.stop();
			((ToggleButton) findViewById(R.id.playButton)).setChecked(false);
			Managers.midiManager.reset();
		}
	}

	public void undo(View view) {
		Managers.midiManager.undo();
		GlobalVars.midiView.handleUndo();
	}

	public void levels(View view) {
		GlobalVars.midiView.toggleLevelsView();
		if (GlobalVars.midiView.getViewState() == MidiView.State.TO_LEVELS_VIEW) {
			fadeListener.setFadeOut(false);
			levelsGroup.startAnimation(fadeIn);
		} else {
			fadeListener.setFadeOut(true);
			levelsGroup.startAnimation(fadeOut);
		}
	}

	public void delete(View view) {
		Managers.midiManager.deleteSelectedNotes();
	}

	public void volume(View view) {
		volume.setChecked(true);
		pan.setChecked(false);
		pitch.setChecked(false);
		GlobalVars.midiView.setLevelMode(LevelsViewHelper.LevelMode.VOLUME);
	}

	public void pan(View view) {
		volume.setChecked(false);
		pan.setChecked(true);
		pitch.setChecked(false);
		GlobalVars.midiView.setLevelMode(LevelsViewHelper.LevelMode.PAN);
	}

	public void pitch(View view) {
		volume.setChecked(false);
		pan.setChecked(false);
		pitch.setChecked(true);
		GlobalVars.midiView.setLevelMode(LevelsViewHelper.LevelMode.PITCH);
	}

	public void bpmTap(View view) {
		long tapTime = System.currentTimeMillis();
		float millisElapsed = tapTime - lastTapTime;
		lastTapTime = tapTime;
		float bpm = 60000 / millisElapsed;
		// if bpm is far below MIN limit, consider this the first tap,
		// otherwise,
		// if it's under but close, set to MIN_BPM
		if (bpm < MidiManager.MIN_BPM - 10)
			return;
		MidiManager.setBPM(bpm);
	}

	public void addTrack(View view) {
		selectInstrumentAlert.show();
	}

	@Override
	public void muteToggled(int track, boolean mute) {
		Managers.playbackManager.muteTrack(track, mute);
	}
	
	@Override
	public void soloToggled(int track, boolean solo) {
		Managers.playbackManager.soloTrack(track, solo);
	}
	
	@Override
	public void trackLongPressed(int track) {
		Managers.midiManager.selectRow(track);
	}
	
	@Override
	public void trackClicked(int track) {
		launchSampleEditActivity(track);
	}
	
	private void addTrack(int instrumentType) {
		String instrumentName = GlobalVars.tracks.get(instrumentType).instrumentName;
		addTrack(GlobalVars.tracks.get(instrumentType).getSampleBytes(0));
		GlobalVars.tracks.add(new Track(instrumentName, GlobalVars.instrumentIcons.get(instrumentName)));
		GlobalVars.currentInstruments.add(instrumentName);
		GlobalVars.midiView.updateTracks();
		// launch sample edit activity for the newly added track
		launchSampleEditActivity(GlobalVars.tracks.size() - 1);
	}
	
	public static native void addTrack(byte[] bytes);

	public static native boolean createAudioPlayer();

	public static native void createEngine();

	public static native void shutdown();

	/** Load jni .so on initialization */
	static {
		System.loadLibrary("nativeaudio");
	}
}