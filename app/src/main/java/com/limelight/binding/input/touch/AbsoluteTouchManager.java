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
        float x, y, pressure, contactMajor, contactMinor, rotation;
    }

    /**
     * To be called directly from the onTouchEvent method of your StreamView.
     */
    public boolean handleTouchEvent(MotionEvent event, View targetView) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);

        // Calculation of offsets to handle any black bars (letterboxing)
        float offsetX = targetView.getX();
        float offsetY = targetView.getY();
        
        // View dimensions to normalize coordinates (if required by your protocol)
        // float width = targetView.getWidth();
        // float height = targetView.getHeight();

        switch (action) {
            case MotionEvent.ACTION_DOWN:          // First finger touches the screen
            case MotionEvent.ACTION_POINTER_DOWN: { // Additional finger touches the screen
                TouchData data = new TouchData();
                data.x = event.getX(actionIndex) - offsetX;
                data.y = event.getY(actionIndex) - offsetY;
                data.pressure = event.getPressure(actionIndex);
                // You can also add contactMajor, contactMinor and rotation if your device supports them
                
                // Register the finger using its UNIQUE ID
                activePointers.put(pointerId, data);

                // Communicate to Windows: "New touch placed"
                conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_DOWN, pointerId,
                        data.x, data.y, data.pressure, 
                        data.contactMajor, data.contactMinor, data.rotation);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // For maximum fluidity, first send "historical" points (micro-movements 
                // captured by the 120Hz/240Hz display between Android frames).
                int historySize = event.getHistorySize();
                int pointerCount = event.getPointerCount();

                for (int h = 0; h < historySize; h++) {
                    for (int i = 0; i < pointerCount; i++) {
                        int id = event.getPointerId(i);
                        if (activePointers.get(id) != null) {
                            float hX = event.getHistoricalX(i, h) - offsetX;
                            float hY = event.getHistoricalY(i, h) - offsetY;
                            float hP = event.getHistoricalPressure(i, h);
                            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, id,
                                    hX, hY, hP, 0, 0, 0);
                        }
                    }
                }

                // Then send current positions of all fingers on the screen
                for (int i = 0; i < pointerCount; i++) {
                    int id = event.getPointerId(i);
                    TouchData data = activePointers.get(id);
                    
                    if (data != null) {
                        data.x = event.getX(i) - offsetX;
                        data.y = event.getY(i) - offsetY;
                        data.pressure = event.getPressure(i);

                        conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_MOVE, id,
                                data.x, data.y, data.pressure, 
                                data.contactMajor, data.contactMinor, data.rotation);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:          // Last finger left up
            case MotionEvent.ACTION_POINTER_UP: { // One finger up (but others remain)
                TouchData data = activePointers.get(pointerId);
                if (data != null) {
                    // Communicate to Windows: "Finger up" (Pressure forced to 0.0f)
                    conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, pointerId,
                            data.x, data.y, 0.0f, 0, 0, 0);
                    
                    // FUNDAMENTAL: Remove finger from memory to avoid ghost touches
                    activePointers.remove(pointerId);
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                // Android interrupted the touch abnormally (e.g. app went background)
                clearAllGhostTouches();
                break;
            }
        }
        return true;
    }

    /**
     * The Kill-Switch: forcibly raises all remaining hanging fingers.
     * To be called when the activity pauses or the stream stops.
     */
    public void clearAllGhostTouches() {
        for (int i = 0; i < activePointers.size(); i++) {
            int pointerId = activePointers.keyAt(i);
            TouchData data = activePointers.valueAt(i);
            
            // Force release event
            conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, pointerId,
                    data.x, data.y, 0.0f, 0, 0, 0);
        }
        activePointers.clear();
    }
}
