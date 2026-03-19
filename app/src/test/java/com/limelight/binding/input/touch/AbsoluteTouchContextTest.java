package com.limelight.binding.input.touch;

import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.jni.MoonBridge;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class AbsoluteTouchContextTest {

    @After
    public void tearDown() {
        AbsoluteTouchContext.resetPointerCacheForTest();
    }

    @Test
    public void pointerCountGreaterThanOneDoesNotCancelTouch() {
        NvConnection conn = mock(NvConnection.class);
        View view = createView(200, 100, 0, 0);
        AbsoluteTouchContext context = new AbsoluteTouchContext(conn, 0, view, false);

        context.touchDownEvent(20, 10, 0L, true);
        context.setPointerCount(2);

        assertFalse(context.isCancelled());
    }

    @Test
    public void touchMoveScansAllActivePointersFromPrimaryContext() {
        NvConnection conn = mock(NvConnection.class);
        View view = createView(200, 100, 0, 0);
        AbsoluteTouchContext primary = new AbsoluteTouchContext(conn, 0, view, false);
        AbsoluteTouchContext secondary = new AbsoluteTouchContext(conn, 1, view, false);

        primary.setPointerCount(2);
        secondary.setPointerCount(2);

        primary.touchDownEvent(10, 20, 0L, true);
        secondary.touchDownEvent(30, 40, 0L, true);

        secondary.touchMoveEvent(35, 45, 10L);
        primary.touchMoveEvent(15, 25, 10L);

        verify(conn).sendTouchEvent(eq(MoonBridge.LI_TOUCH_EVENT_MOVE), eq(0), anyFloat(), anyFloat(),
                anyFloat(), anyFloat(), anyFloat(), anyShort());
        verify(conn).sendTouchEvent(eq(MoonBridge.LI_TOUCH_EVENT_MOVE), eq(1), anyFloat(), anyFloat(),
                anyFloat(), anyFloat(), anyFloat(), anyShort());
        verify(conn, never()).sendMousePosition(anyShort(), anyShort(), anyShort(), anyShort());
    }

    @Test
    public void absoluteScalingUsesViewOffsetForLetterboxedCoordinates() {
        View view = createView(200, 100, 10, 20);

        float[] scaled = AbsoluteTouchContext.scaleToWindowsAbsolute(view, 110, 70, false);

        assertTrue(scaled[0] > 0.49f && scaled[0] < 0.51f);
        assertTrue(scaled[1] > 0.49f && scaled[1] < 0.51f);
    }

    private static View createView(int width, int height, int x, int y) {
        View view = new View(ApplicationProvider.getApplicationContext());
        view.layout(0, 0, width, height);
        view.setX(x);
        view.setY(y);
        return view;
    }
}
