#ifndef EFFECTS_H
#define EFFECTS_H

#include <stdlib.h>
#include <math.h>
#include <pthread.h>
#include <android/log.h>
#include <jni.h>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <android/asset_manager_jni.h>

#define bool _Bool
#define false 0
#define true 1

#define SAMPLE_RATE 44100.0f
#define INV_SAMPLE_RATE 1.0f/44100.0f

#define CHORUS_ID 2
#define DECIMATE_ID 3
#define DELAY_ID 4
#define FILTER_ID 5
#define FLANGER_ID 6
#define REVERB_ID 7
#define TREMELO_ID 8

#define VOL_PAN_ID 0
#define PITCH_ID 1
#define ADSR_ID 10

#define NUM_EFFECTS 11

typedef struct Effect_t {
	void *config;
	void (*set)(void *, float, float);
	void (*process)(void *, float **, int);
	void (*destroy)(void *);
	bool on;
} Effect;

#include "../generators/generators.h"
#include "../generators/wavfile.h"
#include "midievent.h"
#include "track.h"
#include "ticker.h"

void initEffect(Effect *effect, bool on, void *config,
		void (*set), void (*process), void (*destroy));

void reverse(float buffer[], int begin, int end);
void normalize(float buffer[], int size);

#endif // EFFECTS_H
