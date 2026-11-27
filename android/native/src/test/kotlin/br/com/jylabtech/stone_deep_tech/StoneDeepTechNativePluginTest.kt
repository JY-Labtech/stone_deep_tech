package br.com.jylabtech.stone_deep_tech

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoneDeepTechNativePluginTest {

    private lateinit var plugin: StoneDeepTechNativePlugin
    private val opened = mutableListOf<DeeplinkRequest>()
    private val activitiesStarted get() = opened.size

    @Before
    fun setUp() {
        plugin = StoneDeepTechNativePlugin()
        plugin.gateway = StoneGateway { opened += it }
    }

    private fun call(method: String, args: Map<String, Any?>? = emptyMap()): RecordingResult {
        val result = RecordingResult()
        plugin.onMethodCall(MethodCall(method, args), result)
        assertTrue("$method deve responder exatamente uma vez", result.replies <= 1)
        return result
    }

    private fun init(): RecordingResult = call("initDeeplink", null)

    private val operations = listOf(
        "sendDeeplink" to mapOf<String, Any?>("amount" to 1000, "transactionType" to "DEBIT", "editableAmount" to false),
        "sendCancel" to mapOf<String, Any?>("atk" to "123", "amount" to 1000),
        "sendPrint" to mapOf<String, Any?>("printableContent" to "[]", "showFeedbackScreen" to true),
        "sendReprint" to mapOf<String, Any?>("atk" to "123", "typeCustomer" to "CLIENT")
    )

    // ---- Roteamento ----

    @Test
    fun operationMethodsCoverAllOperations() {
        assertEquals(setOf("sendDeeplink", "sendCancel", "sendPrint", "sendReprint"),
            StoneDeepTechNativePlugin.OPERATION_METHODS)
    }

    @Test
    fun everyOperationOpensStoneDirectlyAndRepliesOnce() {
        for ((method, args) in operations) {
            val result = call(method, args)
            assertNull(method, result.errorCode)
            assertEquals(true, result.successValue)
            assertEquals(1, result.replies)
        }
        assertEquals(operations.size, activitiesStarted)
    }

    @Test
    fun operationsDoNotRequireInit() {
        // initDeeplink só entrega respostas pendentes; não é pré-requisito das operações.
        assertEquals(true, call("sendDeeplink", operations[0].second).successValue)
        assertEquals(1, activitiesStarted)
    }

    @Test
    fun initRepliesSuccessOnce() {
        val result = init()
        assertEquals(true, result.successValue)
        assertEquals(1, result.replies)
        assertEquals(0, activitiesStarted)
    }

    @Test
    fun unknownMethodIsNotImplemented() {
        val result = call("unknown")
        assertTrue(result.notImplementedCalled)
        assertEquals(1, result.replies)
        assertEquals(0, activitiesStarted)
    }

    // ---- Caso de uso certo com os argumentos certos ----

    @Test
    fun sendDeeplinkOpensPaymentWithArguments() {
        call("sendDeeplink", mapOf(
            "amount" to 1550, "editableAmount" to true, "transactionType" to "CREDIT",
            "installmentCount" to 3, "installmentType" to "MERCHANT", "orderId" to 42,
            "returnScheme" to "meuapp"
        ))
        val request = opened.single()
        assertEquals("payment-app", request.scheme)
        assertEquals("pay", request.authority)
        assertEquals(listOf(
            "return_scheme" to "meuapp", "editable_amount" to "1", "amount" to "1550",
            "transaction_type" to "CREDIT", "installment_type" to "MERCHANT",
            "installment_count" to "3", "order_id" to "42"
        ), request.queryParameters)
    }

    @Test
    fun sendDeeplinkAcceptsLongValuesFromDart() {
        // Dart envia int > 2^31 como Long; antes virava ClassCastException ("ERROR").
        val result = call("sendDeeplink", mapOf(
            "amount" to 3_000_000_000L, "orderId" to 1_727_000_000_000L, "returnScheme" to "app"
        ))
        assertEquals(true, result.successValue)
        assertEquals("3000000000", opened.single().param("amount"))
        assertEquals("1727000000000", opened.single().param("order_id"))
    }

    @Test
    fun sendCancelOpensCancelWithArguments() {
        call("sendCancel", mapOf("returnScheme" to "meuapp", "atk" to "ATK1", "amount" to 500, "editableAmount" to true))
        val request = opened.single()
        assertEquals("cancel-app", request.scheme)
        assertEquals("cancel", request.authority)
        assertEquals(listOf(
            "returnscheme" to "meuapp", "atk" to "ATK1", "amount" to "500", "editable_amount" to "true"
        ), request.queryParameters)
    }

    @Test
    fun sendPrintOpensPrinterWithArguments() {
        call("sendPrint", mapOf(
            "schemeReturn" to "meuapp", "printableContent" to "[{\"type\":\"line\",\"content\":\"x\"}]",
            "showFeedbackScreen" to false
        ))
        val request = opened.single()
        assertEquals("printer-app", request.scheme)
        assertEquals("print", request.authority)
        assertEquals(listOf(
            "SCHEME_RETURN" to "meuapp",
            "PRINTABLE_CONTENT" to "[{\"type\":\"line\",\"content\":\"x\"}]",
            "SHOW_FEEDBACK_SCREEN" to "false"
        ), request.queryParameters)
    }

    @Test
    fun sendReprintOpensReprinterWithArguments() {
        call("sendReprint", mapOf(
            "schemeReturn" to "meuapp", "atk" to "ATK2", "typeCustomer" to "MERCHANT", "showFeedbackScreen" to true
        ))
        val request = opened.single()
        assertEquals("reprinter-app", request.scheme)
        assertEquals("reprint", request.authority)
        assertEquals(listOf(
            "SCHEME_RETURN" to "meuapp", "atk" to "ATK2", "TYPE_CUSTOMER" to "MERCHANT",
            "SHOW_FEEDBACK_SCREEN" to "true"
        ), request.queryParameters)
    }

    // ---- Erros da operação ----

    @Test
    fun gatewayFailureRepliesErrorOnce() {
        plugin.gateway = StoneGateway { throw IllegalStateException("No Activity found to handle Intent") }
        for ((method, args) in operations) {
            val result = call(method, args)
            assertEquals(method, "ERROR", result.errorCode)
            assertEquals("No Activity found to handle Intent", result.errorMessage)
            assertFalse(result.successCalled)
            assertEquals(1, result.replies)
        }
    }

    @Test
    fun gatewayFailureWithoutMessageUsesExceptionName() {
        plugin.gateway = StoneGateway { throw UnsupportedOperationException() }
        assertEquals("UnsupportedOperationException", call("sendPrint", operations[2].second).errorMessage)
    }

    @Test
    fun wrongArgumentTypeRepliesErrorWithoutOpeningStone() {
        val result = call("sendDeeplink", mapOf("amount" to "1000", "returnScheme" to "app"))
        assertEquals("ERROR", result.errorCode)
        assertTrue(result.errorMessage!!.contains("amount"))
        assertEquals(0, activitiesStarted)
    }

    @Test
    fun defaultGatewayWithoutEngineRepliesErrorInsteadOfFalseSuccess() {
        // Antes: context nulo -> startActivity ignorado e success(true). Agora: erro explícito.
        val detached = StoneDeepTechNativePlugin()
        val result = RecordingResult()
        detached.onMethodCall(MethodCall("sendDeeplink", mapOf("amount" to 1)), result)
        assertEquals("ERROR", result.errorCode)
        assertEquals(1, result.replies)
    }

    // ---- Respostas da Stone para o Dart ----

    @Test
    fun deeplinkResponseIsSentToDart() {
        val sent = mutableListOf<Pair<String, Any?>>()
        plugin.responseSender = { method, args, callback -> sent += method to args; callback.success(null) }

        plugin.onDeeplinkResponseData("meuapp://pay?code=0&atk=1")
        plugin.onDeeplinkResponseData(null)
        plugin.onDeeplinkResponseData("")

        assertEquals(listOf<Pair<String, Any?>>("onDeeplinkResponse" to "meuapp://pay?code=0&atk=1"), sent)
    }

    @Test
    fun responseBeforeDartHandlerIsDeliveredAfterInit() {
        var dartReady = false
        val delivered = mutableListOf<Any?>()
        plugin.responseSender = { _, args, callback ->
            if (dartReady) { delivered += args; callback.success(null) } else callback.notImplemented()
        }

        plugin.onDeeplinkResponseData("meuapp://print?result=SUCCESS")
        assertEquals(1, plugin.responseDispatcher.pendingCount)
        assertTrue(delivered.isEmpty())

        dartReady = true
        assertEquals(true, init().successValue)
        assertEquals(listOf<Any?>("meuapp://print?result=SUCCESS"), delivered)
        assertEquals(0, plugin.responseDispatcher.pendingCount)
    }

    @Test
    fun responseWithoutChannelIsKeptPending() {
        // Sem engine (channel nulo) o envio padrão não perde a resposta.
        plugin.onDeeplinkResponseData("meuapp://pay?code=0")
        assertEquals(1, plugin.responseDispatcher.pendingCount)
    }

    // ---- Filtro e deduplicação das respostas da Stone ----

    private class FakeIntent(val action: String?, val data: String?) {
        var handled = false
        var markCount = 0
    }

    private val VIEW = "android.intent.action.VIEW"

    private fun deliver(intent: FakeIntent): Boolean = plugin.handleIntent(
        intent.action, intent.data, { intent.handled }, { intent.handled = true; intent.markCount++ }
    )

    private fun recordingSender(): MutableList<Any?> {
        val delivered = mutableListOf<Any?>()
        plugin.responseSender = { _, args, callback -> delivered += args; callback.success(null) }
        return delivered
    }

    @Test
    fun viewIntentOfOtherSchemeIsIgnoredAndNotConsumed() {
        val delivered = recordingSender()
        call("sendDeeplink", mapOf("amount" to 1, "returnScheme" to "meuapp"))

        assertFalse(deliver(FakeIntent(VIEW, "https://loja.com/produto/1")))
        assertFalse(deliver(FakeIntent(VIEW, "outroapp://pay?code=0")))
        assertFalse(deliver(FakeIntent("android.intent.action.MAIN", "meuapp://pay?code=0")))
        assertFalse(deliver(FakeIntent(VIEW, null)))
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun viewIntentBeforeAnyDeeplinkIsNotConsumed() {
        val delivered = recordingSender()
        assertFalse(deliver(FakeIntent(VIEW, "meuapp://pay?code=0")))
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun stoneResponseWithSentReturnSchemeIsConsumedAndDelivered() {
        val delivered = recordingSender()
        call("sendDeeplink", mapOf("amount" to 1, "returnScheme" to "MeuApp"))

        val intent = FakeIntent(VIEW, "meuapp://pay?code=0&atk=1")
        assertTrue(deliver(intent))
        assertEquals(listOf<Any?>("meuapp://pay?code=0&atk=1"), delivered)
        assertTrue(intent.handled)
    }

    @Test
    fun everyOperationRegistersItsReturnScheme() {
        val delivered = recordingSender()
        call("sendCancel", mapOf("atk" to "1", "returnScheme" to "cancelapp"))
        call("sendPrint", mapOf("printableContent" to "[]", "schemeReturn" to "printapp"))
        call("sendReprint", mapOf("atk" to "1", "typeCustomer" to "CLIENT", "schemeReturn" to "reprintapp"))

        for (scheme in listOf("cancelapp", "printapp", "reprintapp")) {
            assertTrue(scheme, deliver(FakeIntent(VIEW, "$scheme://result?code=0")))
        }
        assertEquals(3, delivered.size)
    }

    @Test
    fun recreatedActivityWithSameIntentDoesNotRedeliver() {
        val delivered = recordingSender()
        call("sendPrint", mapOf("printableContent" to "[]", "schemeReturn" to "meuapp"))

        val launchIntent = FakeIntent(VIEW, "meuapp://print?result=SUCCESS")
        assertTrue(deliver(launchIntent))
        // Activity recriada (sem mudança de configuração) com o mesmo intent marcado.
        assertTrue(deliver(launchIntent))
        assertEquals(1, delivered.size)
        assertEquals(1, launchIntent.markCount)
    }

    @Test
    fun distinctIntentsWithSameUriAreBothDelivered() {
        // Duas impressões seguidas com o mesmo resultado são respostas legítimas distintas.
        val delivered = recordingSender()
        call("sendPrint", mapOf("printableContent" to "[]", "schemeReturn" to "meuapp"))

        assertTrue(deliver(FakeIntent(VIEW, "meuapp://print?result=SUCCESS")))
        assertTrue(deliver(FakeIntent(VIEW, "meuapp://print?result=SUCCESS")))
        assertEquals(2, delivered.size)
    }

    @Test
    fun markFailureStillDeliversOnce() {
        val delivered = recordingSender()
        call("sendDeeplink", mapOf("amount" to 1, "returnScheme" to "meuapp"))
        assertTrue(plugin.handleIntent(VIEW, "meuapp://pay?code=0", { false }, { throw UnsupportedOperationException() }))
        assertEquals(1, delivered.size)
    }

    @Test
    fun failedOperationStillRecognisesReturnScheme() {
        // O scheme é registrado antes de abrir; mesmo se a abertura falhar, não há efeito colateral.
        plugin.gateway = StoneGateway { throw IllegalStateException("sem app") }
        call("sendDeeplink", mapOf("amount" to 1, "returnScheme" to "meuapp"))
        assertTrue(plugin.returnSchemes.contains("meuapp"))
    }
}
