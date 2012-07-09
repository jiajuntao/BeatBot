package com.kh.beatbot.view;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public abstract class SurfaceViewBase extends SurfaceView implements
		SurfaceHolder.Callback, Runnable {
	protected EGLContext glContext;
	protected SurfaceHolder sHolder;
	protected Thread t;
	protected static GL10 gl;
	protected boolean running;
	int width;
	int height;
	int fps;

	/**
	 * Make a direct NIO FloatBuffer from an array of floats
	 * 
	 * @param arr
	 *            The array
	 * @return The newly created FloatBuffer
	 */
	public static FloatBuffer makeFloatBuffer(float[] arr) {
		ByteBuffer bb = ByteBuffer.allocateDirect(arr.length * 4);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(arr);
		fb.position(0);
		return fb;
	}

	public static FloatBuffer makeRectFloatBuffer(float x1, float y1, float x2, float y2) {
		return makeFloatBuffer(new float[] { x1, y1, x2, y1, x1,
											 y2, x2, y2 });		
	}
	
	public static void drawRectangle(float x1, float y1, float x2, float y2, float[] color) {
		drawTriangleStrip(makeRectFloatBuffer(x1, y1, x2, y2), color);
	}
	
	public static void drawTriangleStrip(FloatBuffer vb, float[] color) {
		gl.glColor4f(color[0], color[1], color[2], color[3]);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vb);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, vb.capacity() / 2);		
	}

	public static void drawTriangleFan(FloatBuffer vb, float[] color) {
		gl.glColor4f(color[0], color[1], color[2], color[3]);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vb);
		gl.glDrawArrays(GL10.GL_TRIANGLE_FAN, 0, vb.capacity() / 2);		
	}
	
	/**
	 * Constructor
	 * 
	 * @param c
	 *            The View's context.
	 * @param as
	 *            The View's Attribute Set
	 */
	public SurfaceViewBase(Context c, AttributeSet as) {
		this(c, as, -1);
	}

	/**
	 * Constructor for animated views
	 * 
	 * @param c
	 *            The View's context
	 * @param fps
	 *            The frames per second for the animation.
	 */
	public SurfaceViewBase(Context c, AttributeSet as, int fps) {
		super(c, as);
		sHolder = getHolder();
		sHolder.addCallback(this);
		this.fps = fps;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		this.width = width;
		this.height = height;
	}

	public void surfaceCreated(SurfaceHolder holder) {
		t = new Thread(this);
		t.start();
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
		running = false;
		try {
			t.join();
		} catch (InterruptedException ex) {
		}
		t = null;
	}

	public void run() {
		// Much of this code is from GLSurfaceView in the Google API Demos.
		// I encourage those interested to look there for documentation.
		EGL10 egl = (EGL10) EGLContext.getEGL();
		EGLDisplay dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

		int[] version = new int[2];
		egl.eglInitialize(dpy, version);

		int[] configSpec = { EGL10.EGL_RED_SIZE, 4, EGL10.EGL_GREEN_SIZE, 4,
				EGL10.EGL_BLUE_SIZE, 4, EGL10.EGL_NONE };

		EGLConfig[] configs = new EGLConfig[1];
		int[] num_config = new int[1];
		egl.eglChooseConfig(dpy, configSpec, configs, 1, num_config);
		EGLConfig config = configs[0];

		EGLContext context = egl.eglCreateContext(dpy, config,
				EGL10.EGL_NO_CONTEXT, null);

		EGLSurface surface = egl.eglCreateWindowSurface(dpy, config, sHolder,
				null);
		egl.eglMakeCurrent(dpy, surface, surface, context);

		gl = (GL10) context.getGL();
		gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
		gl.glEnable(gl.GL_BLEND);
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);

		init();

		int delta = -1;
		if (fps > 0) {
			delta = 1000 / fps;
		}
		long time = System.currentTimeMillis();

		running = true;
		while (running) {

			if (System.currentTimeMillis() - time < delta) {
				try {
					Thread.sleep(System.currentTimeMillis() - time);
				} catch (InterruptedException ex) {
				}
			}
			drawFrame(gl, width, height);
			egl.eglSwapBuffers(dpy, surface);

			if (egl.eglGetError() == EGL11.EGL_CONTEXT_LOST) {
				Context c = getContext();
				if (c instanceof Activity) {
					((Activity) c).finish();
				}
			}
			time = System.currentTimeMillis();
		}
		egl.eglMakeCurrent(dpy, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
				EGL10.EGL_NO_CONTEXT);
		egl.eglDestroySurface(dpy, surface);
		egl.eglDestroyContext(dpy, context);
		egl.eglTerminate(dpy);
	}

	/**
	 * Create a texture and send it to the graphics system
	 * 
	 * @param gl
	 *            The GL object
	 * @param bmp
	 *            The bitmap of the texture
	 * @param reverseRGB
	 *            Should the RGB values be reversed? (necessary workaround for
	 *            loading .pngs...)
	 * @return The newly created identifier for the texture.
	 */
	protected static void loadTexture(GL10 gl, Bitmap bitmap, int[] textures) {

		// generate one texture pointer
		gl.glGenTextures(1, textures, 0);
		// ...and bind it to our array
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);

		// create nearest filtered texture
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,
				GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,
				GL10.GL_LINEAR);

		// Use Android GLUtils to specify a two-dimensional texture image from
		// our bitmap
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
		// Clean up
		bitmap.recycle();
	}

	private void drawFrame(GL10 gl, int w, int h) {
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
		gl.glViewport(0, 0, width, height);
		gl.glLoadIdentity();
		GLU.gluOrtho2D(gl, 0, width, height, 0);
		drawFrame();
	}

	protected abstract void init();

	protected abstract void drawFrame();

	protected abstract void handleActionDown(int id, float x, float y);

	protected abstract void handleActionPointerDown(MotionEvent e, int id,
			float x, float y);

	protected abstract void handleActionMove(MotionEvent e);

	protected abstract void handleActionPointerUp(MotionEvent e, int id,
			float x, float y);

	protected abstract void handleActionUp(int id, float x, float y);

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		switch (e.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_CANCEL:
			return false;
		case MotionEvent.ACTION_DOWN:
			handleActionDown(e.getPointerId(0), e.getX(0), e.getY(0));
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			int index = (e.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			handleActionPointerDown(e, e.getPointerId(index), e.getX(index),
					e.getY(index));
			break;
		case MotionEvent.ACTION_MOVE:
			index = (e.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			handleActionMove(e);
			break;
		case MotionEvent.ACTION_POINTER_UP:
			index = (e.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			handleActionPointerUp(e, e.getPointerId(index), e.getX(index),
					e.getY(index));
			break;
		case MotionEvent.ACTION_UP:
			handleActionUp(e.getPointerId(0), e.getX(0), e.getY(0));
			break;
		}
		return true;
	}

}