package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoSurfaceBindingTest {

    @Test
    public void sameEngineIsNotRebound() {
        Object engine = new Object();
        VideoSurfaceBinding.Result<Object> result = VideoSurfaceBinding.resolve(engine, engine);
        assertFalse("stable playback must not touch the binding", result.applied());
        assertSame(engine, result.player());
    }

    @Test
    public void replacedEngineIsReboundAfterDetachingTheOldOne() {
        Object oldEngine = new Object();
        Object newEngine = new Object();
        VideoSurfaceBinding.Result<Object> result = VideoSurfaceBinding.resolve(oldEngine, newEngine);
        assertTrue("a rebuilt engine must be handed to the view", result.applied());
        assertTrue("the released engine must be detached first", result.detachedFirst());
        assertSame(newEngine, result.player());
    }

    @Test
    public void emptyViewBindsTheFirstEngineWithoutDetaching() {
        Object engine = new Object();
        VideoSurfaceBinding.Result<Object> result = VideoSurfaceBinding.resolve(null, engine);
        assertTrue(result.applied());
        assertFalse("nothing to detach when the view is empty", result.detachedFirst());
        assertSame(engine, result.player());
    }

    @Test
    public void emptiedSessionUnbindsTheStaleEngine() {
        Object engine = new Object();
        VideoSurfaceBinding.Result<Object> result = VideoSurfaceBinding.resolve(engine, null);
        assertTrue("a view must never keep an engine the session no longer has", result.applied());
        assertTrue(result.detachedFirst());
        assertNull(result.player());
    }

    @Test
    public void emptyViewAndEmptySessionIsANoOp() {
        VideoSurfaceBinding.Result<Object> result = VideoSurfaceBinding.resolve(null, null);
        assertFalse(result.applied());
        assertNull(result.player());
    }

    @Test
    public void boundEngineIsReportedAsItself() {
        Object engine = new Object();
        assertEquals(engine, VideoSurfaceBinding.resolve(engine, engine).player());
    }
}