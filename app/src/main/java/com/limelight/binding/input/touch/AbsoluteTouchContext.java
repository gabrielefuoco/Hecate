package com.limelight.binding.input.touch;

import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.jni.MoonBridge;

public class AbsoluteTouchContext implements TouchContext {
    private static final int MAX_TOUCH_POINTS = 16;
    private static final int WINDOWS_COORDINATE_MAX = 65535;
    private static final float DEFAULT_PRESSURE = 1.0f;

    private static final float[] activePointerX = new float[MAX_TOUCH_POINTS];
    private static final float[] activePointerY = new float[MAX_TOUCH_POINTS];
    private static final boolean[] pointerActive = new boolean[MAX_TOUCH_POINTS];

    private boolean cancelled;
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

        cancelled = false;

        cachePointerLocation(actionIndex, eventX, eventY);
        conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_DOWN, actionIndex,
                activePointerX[actionIndex], activePointerY[actionIndex],
                DEFAULT_PRESSURE, 0.0f, 0.0f, MoonBridge.LI_ROT_UNKNOWN);

        return true;
    }

    private void cachePointerLocation(int pointerId, float x, float y) {
        if (pointerId < 0 || pointerId >= MAX_TOUCH_POINTS) {
            return;
        }

        synchronized (AbsoluteTouchContext.class) {
            float[] scaledCoordinates = scaleToWindowsAbsolute(targetView, x, y, true);
            activePointerX[pointerId] = scaledCoordinates[0];
            activePointerY[pointerId] = scaledCoordinates[1];
            pointerActive[pointerId] = true;
        }
    }

    /**
     * Maps Android touch coordinates to the Windows absolute touch space (0-65535 quantized).
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
    public void touchUpEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return;
        }

        if (actionIndex >= 0 && actionIndex < MAX_TOUCH_POINTS) {
            cachePointerLocation(actionIndex, eventX, eventY);
            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, actionIndex,
                    activePointerX[actionIndex], activePointerY[actionIndex],
                    DEFAULT_PRESSURE, 0.0f, 0.0f, MoonBridge.LI_ROT_UNKNOWN);
            synchronized (AbsoluteTouchContext.class) {
                pointerActive[actionIndex] = false;
            }
        }

    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return true;
        }

        cachePointerLocation(actionIndex, eventX, eventY);

        if (actionIndex != 0) {
            return true;
        }

        int pointerLimit = Math.min(Math.max(pointerCount, 1), MAX_TOUCH_POINTS);
        synchronized (AbsoluteTouchContext.class) {
            for (int pointerId = 0; pointerId < pointerLimit; pointerId++) {
                if (!pointerActive[pointerId]) {
                    continue;
                }

                conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, pointerId,
                        activePointerX[pointerId], activePointerY[pointerId],
                        DEFAULT_PRESSURE, 0.0f, 0.0f, MoonBridge.LI_ROT_UNKNOWN);
            }
        }

        return true;
    }

    @Override
    public void cancelTouch() {
        cancelled = true;
        if (actionIndex >= 0 && actionIndex < MAX_TOUCH_POINTS) {
            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_CANCEL, actionIndex,
                    activePointerX[actionIndex], activePointerY[actionIndex],
                    DEFAULT_PRESSURE, 0.0f, 0.0f, MoonBridge.LI_ROT_UNKNOWN);
            synchronized (AbsoluteTouchContext.class) {
                pointerActive[actionIndex] = false;
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
        synchronized (AbsoluteTouchContext.class) {
            for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
                activePointerX[i] = 0.0f;
                activePointerY[i] = 0.0f;
                pointerActive[i] = false;
            }
        }
    }
}
