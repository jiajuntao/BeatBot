package com.kh.beatbot.effect;

import com.kh.beatbot.R;
import com.kh.beatbot.global.GlobalVars;

public class Delay extends Effect {
	public Delay(String name, int trackNum) {
		super(name, trackNum);
		paramsLinked = true;
	}
	
	// keep track of what right channel was before linking
	// so we can go back after disabling link
	// by default, channels are linked, so no memory is needed
	public float rightChannelLevelMemory = -1;
	public boolean rightChannelBeatSyncMemory = true;
	
	@Override
	public void initParams() {
		numParams = 4;
		if (GlobalVars.params[trackNum][effectNum].isEmpty()) {
			GlobalVars.params[trackNum][effectNum].add(new EffectParam(true, true, "ms"));
			GlobalVars.params[trackNum][effectNum].add(new EffectParam(true, true, "ms"));
			GlobalVars.params[trackNum][effectNum].add(new EffectParam(false, false, ""));
			GlobalVars.params[trackNum][effectNum].add(new EffectParam(false, false, ""));
		}
	}

	@Override
	public int getParamLayoutId() {
		return R.layout.delay_param_layout;
	}
	
	@Override
	public int getOnDrawableId() {
		return R.drawable.delay_label_on;
	}
	
	@Override
	public int getOffDrawableId() {
		return R.drawable.delay_label_off;
	}
	
	@Override
	public void setEffectOnNative(boolean on) {
		setDelayOn(trackNum, on);
	}
	
	@Override
	public void setParamNative(int paramNum, float paramLevel) {
		setDelayParam(trackNum, paramNum, paramLevel);
	}
	
	@Override
	public void setParamsLinked(boolean linked) {
		super.setParamsLinked(linked);
		setDelayLinkChannels(trackNum, linked);
	}
	
	public static native void setDelayOn(int trackNum, boolean on);
	public static native void setDelayParam(int trackNum, int paramNum,
			float param);
	public static native void setDelayLinkChannels(int trackNum, boolean link);
}