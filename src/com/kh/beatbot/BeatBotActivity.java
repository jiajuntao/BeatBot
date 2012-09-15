package com.kh.beatbot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Context;
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
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.kh.beatbot.global.GlobalVars;
import com.kh.beatbot.manager.Managers;
import com.kh.beatbot.manager.MidiManager;
import com.kh.beatbot.manager.PlaybackManager;
import com.kh.beatbot.manager.RecordManager;
import com.kh.beatbot.view.MidiView;
import com.kh.beatbot.view.ThresholdBarView;
import com.kh.beatbot.view.helper.LevelsViewHelper;

public class BeatBotActivity extends Activity {
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

	public class IconLongClickListener implements OnLongClickListener {
		@Override
		public boolean onLongClick(View view) {
			int position = (Integer) view.getTag();
			if (view.getId() == R.id.icon) {
				Managers.midiManager.selectRow(position);
			}
			return true;
		}
	}

	public class SampleRowAdapterAndOnClickListener extends
			ArrayAdapter<String> implements AdapterView.OnClickListener {
		int resourceId;
		ArrayList<ToggleButton> soloButtons = new ArrayList<ToggleButton>();

		public SampleRowAdapterAndOnClickListener(Context context,
				int resourceId, String[] sampleTypes) {
			super(context, resourceId, sampleTypes);
			this.resourceId = resourceId;
		}

		@Override
		public void onClick(View view) {
			int position = (Integer) view.getTag();
			if (view.getId() == R.id.icon) {
				// open new intent for sample edit view
				// and pass the number of the sample to the intent as extras
				Intent intent = new Intent();
				intent.setClass(this.getContext(), SampleEditActivity.class);
				intent.putExtra("trackNum", position);
				startActivity(intent);
			} else if (view.getId() == R.id.mute) {
				ToggleButton muteButton = (ToggleButton) view;
				if (muteButton.isChecked())
					Managers.playbackManager.muteTrack(position);
				else
					Managers.playbackManager.unmuteTrack(position);
			} else if (view.getId() == R.id.solo) {
				ToggleButton soloButton = (ToggleButton) view;
				if (soloButton.isChecked()) {
					Managers.playbackManager.soloTrack(position);
					for (ToggleButton otherSoloButton : soloButtons) {
						if (otherSoloButton.isChecked()
								&& !otherSoloButton.equals(soloButton)) {
							otherSoloButton.setChecked(false);
						}
					}
				} else
					Managers.playbackManager.unsoloTrack(position);
			}
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = getLayoutInflater().inflate(resourceId, parent, false);
			ImageButton icon = (ImageButton) view.findViewById(R.id.icon);
			ToggleButton mute = (ToggleButton) view.findViewById(R.id.mute);
			ToggleButton solo = (ToggleButton) view.findViewById(R.id.solo);
			soloButtons.add(solo);
			icon.setTag(position);
			mute.setTag(position);
			solo.setTag(position);
			icon.setOnClickListener(this);
			mute.setOnClickListener(this);
			solo.setOnClickListener(this);
			icon.setOnLongClickListener(iconLongClickListener);
			icon.setBackgroundResource(GlobalVars.instrumentIcons[position]);
			return view;
		}
	}

	private Animation fadeIn, fadeOut;
	// these are used as variables for convenience, since they are reference
	// frequently
	private ToggleButton volume, pan, pitch;
	private ViewGroup levelsGroup;
	private FadeListener fadeListener;
	private ListView sampleListView;
	private static AssetManager assetManager;

	private IconLongClickListener iconLongClickListener = new IconLongClickListener();

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

	private void initSampleListView() {
		SampleRowAdapterAndOnClickListener adapter = new SampleRowAdapterAndOnClickListener(
				this, R.layout.sample_row, GlobalVars.defaultInstruments);
		sampleListView = (ListView) findViewById(R.id.sampleListView);
		sampleListView.setAdapter(adapter);
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
		for (String sampleType : GlobalVars.instrumentNames) {
			// the sample folder for this sample type does not yet exist.
			// create it and write all assets of this type to the folder
			copyFromAssetsToExternal(sampleType);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initAndroidSettings();
		copyAllSamplesToStorage();
		setContentView(R.layout.main);

		initLevelsIconGroup();
		initSampleListView();
		GlobalVars.init(GlobalVars.instrumentNames.length - 1); // minus 1 for
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
		for (int trackNum = 0; trackNum < GlobalVars.numTracks; trackNum++) {
			addTrack(GlobalVars.getSampleBytes(trackNum, 0));
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

	public static native void addTrack(byte[] bytes);
	public static native boolean createAudioPlayer();
	public static native void createEngine();
	public static native void shutdown();

	/** Load jni .so on initialization */
	static {
		System.loadLibrary("nativeaudio");
	}
}