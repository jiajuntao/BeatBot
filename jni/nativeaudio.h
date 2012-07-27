#ifndef NATIVEAUDIO_H
#define NATIVEAUDIO_H

#include "effects.h"
#include "wavfile.h"

#include <assert.h>
#include <jni.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
#include <stdbool.h>
#include <sys/time.h>
#include <sys/types.h>

#include <fftw3.h>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <android/asset_manager_jni.h>

#define CONV16BIT 32768

static SLDataFormat_PCM format_pcm = { SL_DATAFORMAT_PCM, 2,
		SL_SAMPLINGRATE_44_1, SL_PCMSAMPLEFORMAT_FIXED_16,
		SL_PCMSAMPLEFORMAT_FIXED_16, SL_SPEAKER_FRONT_LEFT
				| SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN };

typedef struct MidiEvent_ {
	float volume;
	float pan;
	float pitch;
	long onTick;
	long offTick;
	bool muted;
} MidiEvent;

typedef struct MidiEventNode_ {
	MidiEvent *event;
	MidiEventNode *next;
} MidiEventNode;

typedef struct Track_ {
	Effect effects[NUM_EFFECTS];
	float currBufferFloat[2][BUFF_SIZE];
	short currBufferShort[BUFF_SIZE * 2];
	Generator *generator;
	MidiEventNode *eventHead;

	float volume, pan, pitch;

	SLObjectItf outputPlayerObject;
	SLPlayItf outputPlayerPlay;
	SLMuteSoloItf outputPlayerMuteSolo;
	SLPlaybackRateItf outputPlayerPitch;
	SLAndroidSimpleBufferQueueItf outputBufferQueue;

	bool armed;
	bool playing;
	bool mute;
	bool solo;
} Track;

Track *tracks;
int numTracks;

MidiEvent *findEvent(MidiEventNode *midiEventHead, long tick);
void printLinkedList(MidiEventNode *head);
void playTrack(int trackNum, float volume, float pan, float pitch);
void stopTrack(int trackNum);
void stopAll();
void syncAll(); // sync all BPM-syncable events to (new) BPM

#endif // NATIVEAUDIO_H
