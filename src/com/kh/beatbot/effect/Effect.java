package com.kh.beatbot.effect;

import android.util.FloatMath;

import com.kh.beatbot.global.GlobalVars;
import com.kh.beatbot.manager.MidiManager;

public abstract class Effect {
	public class EffectParam {
		public float level, viewLevel, scale = 1;
		public int topBeatNum = 1, bottomBeatNum = 1;
		public boolean hz = false;
		public boolean beatSync;
		public boolean logScale;
		public String unitString;

		public EffectParam(boolean logScale, boolean beatSync, String unitString) {
			level = viewLevel = 0.5f;
			this.beatSync = beatSync;
			this.logScale = logScale;
			this.unitString = unitString;
		}
	}
	
	public int effectNum;
	protected int trackNum;
	protected int numParams;
	
	public String name;
	public boolean on = false;
	protected boolean paramsLinked = false;
	
	public Effect(String name, int trackNum) {
		this.name = name;
		this.trackNum = trackNum;
		this.effectNum = GlobalVars.effects[trackNum].size();
		GlobalVars.effects[trackNum].add(this);
		initParams();
	}
	
	public void setOn(boolean on) {
		this.on = on;
		setEffectOnNative(on);
	}
	
	public String getName() {
		return name;
	}
	
	public int getNumParams() {
		return numParams;
	}
	
	public boolean paramsLinked() {
		return paramsLinked;
	}
	
	public void setParamsLinked(boolean linked) {
		this.paramsLinked = linked;
	}
	
	public EffectParam getParam(int paramNum) {
		return GlobalVars.params[trackNum][effectNum].get(paramNum);
	}
	
	public String getParamValueString(int paramNum) {
		EffectParam param = getParam(paramNum);
		if (param.beatSync)
			return param.topBeatNum + (param.bottomBeatNum == 1 ? "" : "/" + param.bottomBeatNum);
		else
			return String.format("%.2f", param.level * param.scale) + " " + param.unitString;
	}
	
	public final void setParamLevel(int paramNum, float level) {
		EffectParam param = getParam(paramNum);
		param.viewLevel = level;
		setParamLevel(param, level);
		setParamNative(paramNum, param.level);
	}
	
	public void setParamLevel(EffectParam param, float level) {
		if (param.beatSync) {
			quantizeToBeat(param, level);
		} else if (param.logScale) {
			logScaleLevel(param, level);
		} else {
			param.level = level;
		}
	}
	
	protected static void logScaleLevel(EffectParam param, float level) {
		param.level = (float) (Math.pow(9, level) - 1) / 8;
		if (param.hz)
			param.level *= 32;
	}
	
	protected static void quantizeToBeat(EffectParam param, float level) {
		param.topBeatNum = getTopBeatNum((int)FloatMath.ceil(level * 14));
		param.bottomBeatNum = getBottomBeatNum((int)FloatMath.ceil(level * 14));
		param.level = (60f / (MidiManager.getBPM()) * ((float)param.topBeatNum / (float)param.bottomBeatNum));
		if (param.hz)
			param.level = 1 / param.level;
	}
	
	private static int getTopBeatNum(int which) {
		switch(which) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4: return 1;
		case 5: return 3;
		case 6: return 1;
		case 7: return 5;
		case 8: return 1;
		case 9: return 3;
		case 10: return 1;
		case 11: return 3;
		case 12: return 1;
		case 13: return 3;
		case 14: return 2;
		default: return 1;
		}
	}
	
	private static int getBottomBeatNum(int which) {
		switch(which) {
		case 0:
		case 1: return 16;
		case 2: return 12;
		case 3: return 8;
		case 4: return 6;
		case 5: return 16;
		case 6: return 4;
		case 7: return 16;
		case 8: return 3;
		case 9: return 8;
		case 10: return 2;
		case 11: return 4;
		case 12: return 1;
		case 13: return 2;
		case 14: return 1;
		default: return 1;
		}
	}
	
	public abstract void setEffectOnNative(boolean on);
	public abstract void setParamNative(int paramNum, float paramLevel);
	
	protected abstract void initParams();
	public abstract int getParamLayoutId();
	public abstract int getOnDrawableId();
	public abstract int getOffDrawableId();
}