package com.xnotes.pen

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.xnotes.gl.GlWetPad
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WetPadLifecycleTest {
    @Test fun reattachWaitsForSlowWorkerAndCancelledRetryCannotRestartIt() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val runner = PenTestRunner.instance
        val worker = HandlerThread("wet-pad-delayed-teardown").also { it.start() }
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val handler = Handler(worker.looper)
        handler.post { entered.countDown(); unblock.await(10, TimeUnit.SECONDS) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        lateinit var pad: GlWetPad
        val threadField = GlWetPad::class.java.getDeclaredField("thread").apply { isAccessible = true }
        val handlerField = GlWetPad::class.java.getDeclaredField("handler").apply { isAccessible = true }
        var failure: Throwable? = null
        try {
            runner.runOnMainSync {
                try {
                    pad = GlWetPad(runner.targetContext)
                    val layer = android.view.SurfaceControl.Builder()
                        .setName("wet-pad-lifecycle-test").setBufferSize(16, 16).build()
                    GlWetPad::class.java.getDeclaredField("layer").apply { isAccessible = true }.set(pad, layer)
                    GlWetPad::class.java.getDeclaredField("target").apply { isAccessible = true }
                        .set(pad, android.view.Surface(layer))
                    threadField.set(pad, worker)
                    handlerField.set(pad, handler)
                    pad.surfaceDestroyed(pad.holder)
                    assertTrue("force teardown past its 500ms deadline", worker.isAlive)
                    assertNull(handlerField.get(pad))
                    pad.surfaceCreated(pad.holder)
                    assertSame("reattach must not start a concurrent EGL writer", worker, threadField.get(pad))
                    assertNull(handlerField.get(pad))
                    assertFalse(pad.ready)
                    // Detach again before retirement finishes. The scheduled retry is now stale.
                    pad.surfaceDestroyed(pad.holder)
                } catch (t: Throwable) { failure = t }
            }
        } finally {
            unblock.countDown()
            worker.quitSafely()
            worker.join(3000)
        }
        failure?.let { throw it }
        assertFalse(worker.isAlive)
        val retryWindow = CountDownLatch(1)
        Handler(runner.targetContext.mainLooper).postDelayed({ retryWindow.countDown() }, 80)
        assertTrue(retryWindow.await(2, TimeUnit.SECONDS))
        runner.runOnMainSync {
            try {
                assertSame(worker, threadField.get(pad))
                assertNull(handlerField.get(pad))
                assertFalse(pad.ready)
            } catch (t: Throwable) { failure = t }
        }
        failure?.let { throw it }
    }
}
