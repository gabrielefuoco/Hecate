package com.limelight.binding.input.touch;

import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.jni.MoonBridge;

public class AbsoluteTouchManager {

    private final NvConnection conn;
    
    // Use a SparseArray to map POINTER_ID to its current state.
    // No more static variables: no risk of ghost touches between sessions!
    private final SparseArray<TouchData> activePointers = new SparseArray<>();

    public AbsoluteTouchManager(NvConnection conn) {
        this.conn = conn;
    }

    // Lightweight internal class to store single touch data
    private static class TouchData {
        float x, y, pressure, contactMajor, contactMinor;
        short rotation;
    }

    private float scaleX(float x, View targetView) {
        int width = Math.max(targetView.getWidth(), 1);
        return Math.max(0.0f, Math.min(x, width)) / (float) width;
    }

    private float scaleY(float y, View targetView) {
        int height = Math.max(targetView.getHeight(), 1);
        return Math.max(0.0f, Math.min(y, height)) / (float) height;
    }

    private void cachePointerData(MotionEvent event, int pointerIndex, View targetView) {
        int pointerId = event.getPointerId(pointerIndex);
        TouchData data = activePointers.get(pointerId);
        if (data == null) {
            data = new TouchData();
            activePointers.put(pointerId, data);
        }

        float offsetX = targetView.getX();
        float offsetY = targetView.getY();

        data.x = scaleX(event.getX(pointerIndex) - offsetX, targetView);
        data.y = scaleY(event.getY(pointerIndex) - offsetY, targetView);
        data.pressure = event.getPressure(pointerIndex);
        data.contactMajor = event.getTouchMajor(pointerIndex);
        data.contactMinor = event.getTouchMinor(pointerIndex);
        // Convert radians to degrees and cast to short
        data.rotation = (short) Math.toDegrees(event.getOrientation(pointerIndex));
    }

    private void clearActivePointer(int pointerId) {
        activePointers.remove(pointerId);
    }

    /**
     * To be called directly from the onTouchEvent method of your StreamView.
     */
    public boolean handleTouchEvent(MotionEvent event, View targetView) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);

        float offsetX = targetView.getX();
        float offsetY = targetView.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                cachePointerData(event, actionIndex, targetView);
                TouchData data = activePointers.get(pointerId);
                conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_DOWN, pointerId,
                        data.x, data.y, data.pressure,
                        data.contactMajor, data.contactMinor, data.rotation);
                break;

            case MotionEvent.ACTION_MOVE: {
                int historySize = event.getHistorySize();
                int pointerCount = event.getPointerCount();

                // Process historical points for maximum fluidity
                for (int h = 0; h < historySize; h++) {
                    for (int i = 0; i < pointerCount; i++) {
                        int id = event.getPointerId(i);
                        if (activePointers.get(id) != null) {
                            float hX = scaleX(event.getHistoricalX(i, h) - offsetX, targetView);
                            float hY = scaleY(event.getHistoricalY(i, h) - offsetY, targetView);
                            float hP = event.getHistoricalPressure(i, h);
                            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, id,
                                    hX, hY, hP, 0.0f, 0.0f, (short)0);
                        }
                    }
                }

                // Process current point
                for (int i = 0; i < pointerCount; i++) {
                    int id = event.getPointerId(i);
                    if (activePointers.get(id) != null) {
                        cachePointerData(event, i, targetView);
                        TouchData moveData = activePointers.get(id);
                        conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, id,
                                moveData.x, moveData.y, moveData.pressure,
                                moveData.contactMajor, moveData.contactMinor, moveData.rotation);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                TouchData upData = activePointers.get(pointerId);
                if (upData != null) {
                    // Coordinates are already scaled in TouchData if they were updated in MOVE
                    // but ACTION_UP/POINTER_UP might happen without a MOVE immediately before.
                    // Let's ensure upData has current scaled coordinates.
                    cachePointerData(event, actionIndex, targetView);
                    upData = activePointers.get(pointerId);
                    
                    conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, pointerId,
                            upData.x, upData.y, 0.0f, 0.0f, 0.0f, (short)0);
                    clearActivePointer(pointerId);
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
                clearAllGhostTouches();
                break;
        }
        return true;
    }

    /**
     * The Kill-Switch: forcibly raises all remaining hanging fingers.
     * To be called when the activity pauses or the stream stops.
     */
    public void clearAllGhostTouches() {
        int size = activePointers.size();
        for (int i = 0; i < size; i++) {
            int id = activePointers.keyAt(i);
            TouchData data = activePointers.valueAt(i);
            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, id,
                    data.x, data.y, 0.0f, 0.0f, 0.0f, (short)0);
        }
        activePointers.clear();
    }
}
