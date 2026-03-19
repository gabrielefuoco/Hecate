package com.limelight.binding.input.touch;

import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.jni.MoonBridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class AbsoluteTouchContextTest {
    private static final float EXPECTED_CENTER = 0.5f;
    private static final float SCALING_TOLERANCE = 0.01f;

    @Before
    public void setUp() {
        AbsoluteTouchContext.resetPointerCacheForTest();
    }

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
    public void touchMetadataIsForwardedToTouchPackets() {
        NvConnection conn = mock(NvConnection.class);
        View view = createView(200, 100, 0, 0);
        AbsoluteTouchContext context = new AbsoluteTouchContext(conn, 0, view, false);

        context.setPointerCount(1);
        context.updateTouchMetadata(0.7f, 0.2f, 0.1f, (short) 45);
        context.touchDownEvent(20, 10, 0L, true);
        context.touchMoveEvent(30, 20, 10L);
        context.touchUpEvent(30, 20, 20L);

        ArgumentCaptor<Float> pressureCaptor = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> majorCaptor = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> minorCaptor = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Short> rotationCaptor = ArgumentCaptor.forClass(Short.class);

        verify(conn, times(3)).sendTouchEvent(
                org.mockito.ArgumentMatchers.anyByte(),
                eq(0),
                anyFloat(),
                anyFloat(),
                pressureCaptor.capture(),
                majorCaptor.capture(),
                minorCaptor.capture(),
                rotationCaptor.capture()
        );

        for (Float pressure : pressureCaptor.getAllValues()) {
            assertEquals(0.7f, pressure, 0.0001f);
        }
        for (Float major : majorCaptor.getAllValues()) {
            assertEquals(0.2f, major, 0.0001f);
        }
        for (Float minor : minorCaptor.getAllValues()) {
            assertEquals(0.1f, minor, 0.0001f);
        }
        for (Short rotation : rotationCaptor.getAllValues()) {
            assertEquals((short) 45, rotation.shortValue());
        }
    }

    @Test
    public void absoluteScalingUsesViewOffsetForLetterboxedCoordinates() {
        View view = createView(200, 100, 10, 20);

        float[] scaled = AbsoluteTouchContext.scaleToWindowsAbsolute(view, 110, 70, false);

        assertEquals(EXPECTED_CENTER, scaled[0], SCALING_TOLERANCE);
        assertEquals(EXPECTED_CENTER, scaled[1], SCALING_TOLERANCE);
    }

    private static View createView(int width, int height, int x, int y) {
        View view = new View(ApplicationProvider.getApplicationContext());
        view.layout(0, 0, width, height);
        view.setX(x);
        view.setY(y);
        return view;
    }
}
