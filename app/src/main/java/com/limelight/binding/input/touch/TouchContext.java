package com.limelight.binding.input.touch;

public interface TouchContext {
    int getActionIndex();
    void setPointerCount(int pointerCount);
    default void updateTouchMetadata(float pressureOrDistance, float contactAreaMajor, float contactAreaMinor, short rotation) {}
    boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger);
    boolean touchMoveEvent(int eventX, int eventY, long eventTime);
    void touchUpEvent(int eventX, int eventY, long eventTime);
    void cancelTouch();
    boolean isCancelled();
}
