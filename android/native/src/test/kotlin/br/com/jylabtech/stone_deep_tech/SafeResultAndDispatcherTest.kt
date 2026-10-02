package br.com.jylabtech.stone_deep_tech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeResultAndDispatcherTest {

    @Test
    fun safeResultForwardsOnlyFirstReply() {
        val delegate = RecordingResult()
        val safe = SafeResult(delegate, "m")
        assertFalse(safe.hasReplied)

        safe.success(1)
        safe.error("X", "y", null)
        safe.notImplemented()
        safe.success(2)

        assertTrue(safe.hasReplied)
        assertEquals(1, delegate.replies)
        assertEquals(1, delegate.successValue)
    }

    @Test
    fun safeResultErrorAndNotImplementedAreForwarded() {
        val errorDelegate = RecordingResult()
        SafeResult(errorDelegate, "m").error("CODE", "msg", null)
        assertEquals("CODE", errorDelegate.errorCode)

        val notImplDelegate = RecordingResult()
        SafeResult(notImplDelegate, "m").notImplemented()
        assertTrue(notImplDelegate.notImplementedCalled)
    }

    @Test
    fun dispatcherDeliversImmediatelyWhenDartIsReady() {
        val sent = mutableListOf<Pair<String, Any?>>()
        val dispatcher = DeeplinkResponseDispatcher { m, a, cb -> sent += m to a; cb.success(null) }
        dispatcher.dispatch("onDeeplinkResponse", "uri")
        assertEquals(listOf<Pair<String, Any?>>("onDeeplinkResponse" to "uri"), sent)
        assertEquals(0, dispatcher.pendingCount)
    }

    @Test
    fun dispatcherDoesNotRequeueWhenDartHandlerFails() {
        val dispatcher = DeeplinkResponseDispatcher { _, _, cb -> cb.error("E", "falhou", null) }
        dispatcher.dispatch("onDeeplinkResponse", "uri")
        assertEquals(0, dispatcher.pendingCount)
    }

    @Test
    fun dispatcherKeepsPendingInOrderAndFlushes() {
        var ready = false
        val delivered = mutableListOf<Any?>()
        val dispatcher = DeeplinkResponseDispatcher { _, a, cb ->
            if (ready) { delivered += a; cb.success(null) } else cb.notImplemented()
        }
        dispatcher.dispatch("onDeeplinkResponse", "a")
        dispatcher.dispatch("onDeeplinkResponse", "b")
        assertEquals(2, dispatcher.pendingCount)

        dispatcher.flushPending() // ainda sem handler: continua pendente, sem laço infinito
        assertEquals(2, dispatcher.pendingCount)

        ready = true
        dispatcher.flushPending()
        assertEquals(listOf<Any?>("a", "b"), delivered)
        assertEquals(0, dispatcher.pendingCount)
    }

    @Test
    fun dispatcherQueueIsBoundedAndClearable() {
        val dispatcher = DeeplinkResponseDispatcher { _, _, cb -> cb.notImplemented() }
        repeat(DeeplinkResponseDispatcher.MAX_PENDING + 5) { dispatcher.dispatch("onDeeplinkResponse", "u$it") }
        assertEquals(DeeplinkResponseDispatcher.MAX_PENDING, dispatcher.pendingCount)
        dispatcher.clear()
        assertEquals(0, dispatcher.pendingCount)
    }

    @Test
    fun dispatcherKeepsPendingWhenSenderThrows() {
        val dispatcher = DeeplinkResponseDispatcher { _, _, _ -> throw IllegalStateException("sem engine") }
        dispatcher.dispatch("onDeeplinkResponse", "uri")
        assertEquals(1, dispatcher.pendingCount)
    }

    @Test
    fun intentGatewayWithoutContextFails() {
        val gateway = IntentStoneGateway { null }
        try {
            gateway.open(DeeplinkRequest("payment-app", "pay", emptyList()))
            throw AssertionError("deveria falhar")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("anexado"))
        }
    }
}
