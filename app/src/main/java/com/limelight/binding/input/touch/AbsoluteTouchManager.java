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

    private void cachePointerData(MotionEvent event, int pointerIndex) {
        int pointerId = event.getPointerId(pointerIndex);
        TouchData data = activePointers.get(pointerId);
        if (data == null) {
            data = new TouchData();
            activePointers.put(pointerId, data);
        }
        data.x = event.getX(pointerIndex);
        data.y = event.getY(pointerIndex);
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

        // Coordinated are normalized relative to the target view (stream)
        float offsetX = targetView.getX();
        float offsetY = targetView.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                cachePointerData(event, actionIndex);
                TouchData data = activePointers.get(pointerId);
                conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_DOWN, pointerId,
                        data.x - offsetX, data.y - offsetY, data.pressure,
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
                            float hX = event.getHistoricalX(i, h) - offsetX;
                            float hY = event.getHistoricalY(i, h) - offsetY;
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
                        cachePointerData(event, i);
                        TouchData moveData = activePointers.get(id);
                        conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, id,
                                moveData.x - offsetX, moveData.y - offsetY, moveData.pressure,
                                moveData.contactMajor, moveData.contactMinor, moveData.rotation);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                TouchData upData = activePointers.get(pointerId);
                if (upData != null) {
                    conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, pointerId,
                            upData.x - offsetX, upData.y - offsetY, 0.0f, 0.0f, 0.0f, (short)0);
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
