package com.limelight.binding.input.touch;

import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.jni.MoonBridge;

public class AbsoluteTouchContext implements TouchContext {
    private static final int MAX_TOUCH_POINTS = 16;
    private static final int WINDOWS_COORDINATE_MAX = 65535;
    private static final float DEFAULT_PRESSURE = 1.0f;
    private static final float DEFAULT_CONTACT_AREA = 0.0f;
    private static final short DEFAULT_ROTATION = MoonBridge.LI_ROT_UNKNOWN;

    private static final float[] activePointerX = new float[MAX_TOUCH_POINTS];
    private static final float[] activePointerY = new float[MAX_TOUCH_POINTS];
    private static final float[] activePointerPressure = new float[MAX_TOUCH_POINTS];
    private static final float[] activePointerContactAreaMajor = new float[MAX_TOUCH_POINTS];
    private static final float[] activePointerContactAreaMinor = new float[MAX_TOUCH_POINTS];
    private static final short[] activePointerRotation = new short[MAX_TOUCH_POINTS];
    private static final boolean[] pointerActive = new boolean[MAX_TOUCH_POINTS];
    private static final Object POINTER_LOCK = new Object();

    static {
        initializePointerCache();
    }

    private volatile boolean cancelled;
    private int pointerCount;

    private final NvConnection conn;
    private final int actionIndex;
    private final View targetView;

    @SuppressWarnings("unused")
    public AbsoluteTouchContext(NvConnection conn, int actionIndex, View view, boolean swapped)
    {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.targetView = view;
    }

    @Override
    public int getActionIndex()
    {
        return actionIndex;
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger)
    {
        if (!isNewFinger) {
            return true;
        }

        if (pointerCount <= 1) {
            resetAllPointerState();
        }

        cancelled = false;

        cachePointerLocation(actionIndex, eventX, eventY);
        conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_DOWN, actionIndex,
                activePointerX[actionIndex], activePointerY[actionIndex],
                activePointerPressure[actionIndex], activePointerContactAreaMajor[actionIndex],
                activePointerContactAreaMinor[actionIndex], activePointerRotation[actionIndex]);

        return true;
    }

    private void cachePointerLocation(int pointerId, float x, float y) {
        if (pointerId < 0 || pointerId >= MAX_TOUCH_POINTS) {
            return;
        }

        float[] scaledCoordinates = scaleToWindowsAbsolute(targetView, x, y, true);
        activePointerX[pointerId] = scaledCoordinates[0];
        activePointerY[pointerId] = scaledCoordinates[1];
        pointerActive[pointerId] = true;
    }

    private void cachePointerMetadata(int pointerId, float pressureOrDistance, float contactAreaMajor,
                                      float contactAreaMinor, short rotation) {
        if (pointerId < 0 || pointerId >= MAX_TOUCH_POINTS) {
            return;
        }

        activePointerPressure[pointerId] = Math.max(pressureOrDistance, 0.0f);
        activePointerContactAreaMajor[pointerId] = Math.max(contactAreaMajor, 0.0f);
        activePointerContactAreaMinor[pointerId] = Math.max(contactAreaMinor, 0.0f);
        activePointerRotation[pointerId] = rotation;
    }

    /**
     * Maps Android touch coordinates to the Windows absolute touch space (0-65535 quantized).
     * The target view is expected to be the stream content viewport (StreamContainer), which is
     * already measured to the stream aspect ratio in FIT mode, so black bars remain outside this
     * coordinate space.
     * If coordinates are not view-relative, this method subtracts the target view offset first
     * so letterboxed/padded layouts are handled correctly.
     */
    static float[] scaleToWindowsAbsolute(View targetView, float x, float y, boolean isViewRelative) {
        int width = Math.max(targetView.getWidth(), 1);
        int height = Math.max(targetView.getHeight(), 1);

        float localX = isViewRelative ? x : x - targetView.getX();
        float localY = isViewRelative ? y : y - targetView.getY();

        float normalizedX = Math.max(0.0f, Math.min(localX, width)) / width;
        float normalizedY = Math.max(0.0f, Math.min(localY, height)) / height;

        // Quantize to Windows 16-bit absolute touch coordinates, then convert back to normalized
        // floats because sendTouchEvent() expects normalized X/Y in the Java API.
        int x16 = Math.round(normalizedX * WINDOWS_COORDINATE_MAX);
        int y16 = Math.round(normalizedY * WINDOWS_COORDINATE_MAX);

        return new float[] {
                x16 / (float)WINDOWS_COORDINATE_MAX,
                y16 / (float)WINDOWS_COORDINATE_MAX
        };
    }

    @Override
    public void updateTouchMetadata(float pressureOrDistance, float contactAreaMajor, float contactAreaMinor, short rotation) {
        cachePointerMetadata(actionIndex, pressureOrDistance, contactAreaMajor, contactAreaMinor, rotation);
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return;
        }

        if (actionIndex >= 0 && actionIndex < MAX_TOUCH_POINTS) {
            cachePointerLocation(actionIndex, eventX, eventY);
            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, actionIndex,
                    activePointerX[actionIndex], activePointerY[actionIndex],
                    activePointerPressure[actionIndex], activePointerContactAreaMajor[actionIndex],
                    activePointerContactAreaMinor[actionIndex], activePointerRotation[actionIndex]);
            resetPointerState(actionIndex);
        }

    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return true;
        }

        if (actionIndex < 0 || actionIndex >= MAX_TOUCH_POINTS) {
            return true;
        }

        cachePointerLocation(actionIndex, eventX, eventY);

        if (pointerActive[actionIndex]) {
            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, actionIndex,
                    activePointerX[actionIndex], activePointerY[actionIndex],
                    activePointerPressure[actionIndex], activePointerContactAreaMajor[actionIndex],
                    activePointerContactAreaMinor[actionIndex], activePointerRotation[actionIndex]);
        }

        return true;
    }

    @Override
    public void cancelTouch() {
        synchronized (POINTER_LOCK) {
            if (cancelled) {
                return;
            }
            if (actionIndex >= 0 && actionIndex < MAX_TOUCH_POINTS && pointerActive[actionIndex]) {
                conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, actionIndex,
                        activePointerX[actionIndex], activePointerY[actionIndex],
                        0.0f, 0.0f, 0.0f, (short) 0);
                resetPointerState(actionIndex);
            }
            cancelled = true;
        }
    }

    public static void clearAllGhostTouches(NvConnection conn) {
        synchronized (POINTER_LOCK) {
            for (int pointerId = 0; pointerId < MAX_TOUCH_POINTS; pointerId++) {
                if (!pointerActive[pointerId]) {
                    continue;
                }

                conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, pointerId,
                        activePointerX[pointerId], activePointerY[pointerId],
                        0.0f, 0.0f, 0.0f, (short) 0);
                resetPointerState(pointerId);
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setPointerCount(int pointerCount) {
        this.pointerCount = Math.max(pointerCount, 0);
    }

    @VisibleForTesting
    static void resetPointerCacheForTest() {
        initializePointerCache();
    }

    private static void initializePointerCache() {
        resetAllPointerState();
    }

    private static void resetAllPointerState() {
        for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
            resetPointerState(i);
        }
    }

    private static void resetPointerState(int pointerId) {
        activePointerX[pointerId] = 0.0f;
        activePointerY[pointerId] = 0.0f;
        activePointerPressure[pointerId] = DEFAULT_PRESSURE;
        activePointerContactAreaMajor[pointerId] = DEFAULT_CONTACT_AREA;
        activePointerContactAreaMinor[pointerId] = DEFAULT_CONTACT_AREA;
        activePointerRotation[pointerId] = DEFAULT_ROTATION;
        pointerActive[pointerId] = false;
    }
}
