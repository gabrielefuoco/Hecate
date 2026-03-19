package com.limelight;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameMultiTouchBehaviorTest {

    @Test
    public void legacyGesturesDisabledWhenNativeMultiTouchEnabled() {
        assertFalse(Game.shouldHandleLegacyMultiTouchGestures(true));
    }

    @Test
    public void legacyGesturesEnabledWhenNativeMultiTouchDisabled() {
        assertTrue(Game.shouldHandleLegacyMultiTouchGestures(false));
    }
}
