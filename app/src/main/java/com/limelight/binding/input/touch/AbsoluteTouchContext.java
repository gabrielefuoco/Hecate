package com.limelight.binding.input.touch;

import android.view.View;

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

    private void cachePointerLocation(int pointerId, float eventX, float eventY) {
        if (pointerId < 0 || pointerId >= MAX_TOUCH_POINTS) {
            return;
        }

        float[] scaledCoordinates = scaleToWindowsAbsolute(targetView, eventX, eventY, true);
        activePointerX[pointerId] = scaledCoordinates[0];
        activePointerY[pointerId] = scaledCoordinates[1];
        pointerActive[pointerId] = true;
    }

    static float[] scaleToWindowsAbsolute(View targetView, float rawX, float rawY, boolean isViewRelative) {
        int width = Math.max(targetView.getWidth(), 1);
        int height = Math.max(targetView.getHeight(), 1);

        float localX = isViewRelative ? rawX : rawX - targetView.getX();
        float localY = isViewRelative ? rawY : rawY - targetView.getY();

        float normalizedX = Math.max(0.0f, Math.min(localX, width)) / width;
        float normalizedY = Math.max(0.0f, Math.min(localY, height)) / height;

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
            pointerActive[actionIndex] = false;
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

        int activePointers = Math.min(Math.max(pointerCount, 1), MAX_TOUCH_POINTS);
        for (int pointerId = 0; pointerId < activePointers; pointerId++) {
            if (!pointerActive[pointerId]) {
                continue;
            }

            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, pointerId,
                    activePointerX[pointerId], activePointerY[pointerId],
                    DEFAULT_PRESSURE, 0.0f, 0.0f, MoonBridge.LI_ROT_UNKNOWN);
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
            pointerActive[actionIndex] = false;
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

    static void resetPointerCacheForTest() {
        for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
            activePointerX[i] = 0.0f;
            activePointerY[i] = 0.0f;
            pointerActive[i] = false;
        }
    }
}
