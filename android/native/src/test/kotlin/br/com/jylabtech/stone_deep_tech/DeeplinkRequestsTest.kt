package br.com.jylabtech.stone_deep_tech

import io.flutter.plugin.common.MethodCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeeplinkRequestsTest {

    @Test
    fun paymentWithOnlyDefaults() {
        val request = DeeplinkRequests.fromCall(MethodCall("sendDeeplink", emptyMap<String, Any>()))
        assertEquals("payment-app", request.scheme)
        assertEquals("pay", request.authority)
        assertEquals(listOf("editable_amount" to "0"), request.queryParameters)
    }

    @Test
    fun paymentIgnoresInstallmentTypeWhenNotCredit() {
        val request = DeeplinkRequests.payment(
            amount = 100, editableAmount = false, transactionType = "DEBIT",
            installmentCount = null, installmentType = "MERCHANT", orderId = null, returnScheme = "app"
        )
        assertNull(request.param("installment_type"))
        assertEquals("DEBIT", request.param("transaction_type"))
    }

    @Test
    fun paymentSendsInstallmentTypeForCredit() {
        val request = DeeplinkRequests.payment(
            amount = 100, editableAmount = null, transactionType = "CREDIT",
            installmentCount = 2, installmentType = "ISSUER", orderId = 7, returnScheme = null
        )
        assertEquals("ISSUER", request.param("installment_type"))
        assertEquals("2", request.param("installment_count"))
        assertEquals("7", request.param("order_id"))
        assertNull(request.param("return_scheme"))
        assertEquals("0", request.param("editable_amount"))
    }

    @Test
    fun cancelDefaults() {
        val request = DeeplinkRequests.fromCall(MethodCall("sendCancel", mapOf("atk" to "A")))
        assertEquals(listOf("atk" to "A", "editable_amount" to "false"), request.queryParameters)
    }

    @Test
    fun printDefaults() {
        val request = DeeplinkRequests.fromCall(MethodCall("sendPrint", null))
        assertEquals("printer-app", request.scheme)
        assertEquals(listOf("SHOW_FEEDBACK_SCREEN" to "false"), request.queryParameters)
    }

    @Test
    fun reprintDefaults() {
        val request = DeeplinkRequests.fromCall(MethodCall("sendReprint", mapOf("typeCustomer" to "CLIENT")))
        assertEquals(listOf("TYPE_CUSTOMER" to "CLIENT", "SHOW_FEEDBACK_SCREEN" to "false"), request.queryParameters)
    }

    @Test
    fun unknownMethodIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeeplinkRequests.fromCall(MethodCall("initDeeplink", null))
        }
    }

    @Test
    fun wrongTypesAreRejectedWithKeyName() {
        val badBool = assertThrows(IllegalArgumentException::class.java) {
            DeeplinkRequests.fromCall(MethodCall("sendPrint", mapOf("showFeedbackScreen" to "true")))
        }
        assertTrue(badBool.message!!.contains("showFeedbackScreen"))

        val badString = assertThrows(IllegalArgumentException::class.java) {
            DeeplinkRequests.fromCall(MethodCall("sendCancel", mapOf("atk" to 123)))
        }
        assertTrue(badString.message!!.contains("atk"))

        val badNumber = assertThrows(IllegalArgumentException::class.java) {
            DeeplinkRequests.fromCall(MethodCall("sendDeeplink", mapOf("orderId" to 1.5)))
        }
        assertTrue(badNumber.message!!.contains("orderId"))
    }

    @Test
    fun toStringIsReadable() {
        val request = DeeplinkRequests.cancel("app", "A", 10, false)
        assertEquals("cancel-app://cancel?returnscheme=app&atk=A&amount=10&editable_amount=false", request.toString())
        assertEquals("x://y", DeeplinkRequest("x", "y", emptyList()).toString())
    }

    @Test
    fun returnSchemeIsNormalisedForEveryRequest() {
        assertEquals("meuapp", DeeplinkRequests.payment(null, null, null, null, null, null, " MeuApp ").returnScheme)
        assertEquals("meuapp", DeeplinkRequests.cancel("meuapp://", "A", null, null).returnScheme)
        assertEquals("meuapp", DeeplinkRequests.print("MEUAPP://x", null, null).returnScheme)
        assertEquals("meuapp", DeeplinkRequests.reprint("meuapp", null, null, null).returnScheme)
        assertNull(DeeplinkRequests.payment(null, null, null, null, null, null, null).returnScheme)
        assertNull(DeeplinkRequests.print("  ", null, null).returnScheme)
        // O parâmetro enviado à Stone continua com o valor original.
        assertEquals(" MeuApp ", DeeplinkRequests.payment(null, null, null, null, null, null, " MeuApp ").param("return_scheme"))
    }

    @Test
    fun schemeOfReceivedUri() {
        assertEquals("meuapp", DeeplinkRequest.schemeOf("MeuApp://pay?code=0"))
        assertEquals("meuapp", DeeplinkRequest.schemeOf("meuapp:pay"))
        assertNull(DeeplinkRequest.schemeOf("sem-scheme"))
        assertNull(DeeplinkRequest.schemeOf(null))
        assertNull(DeeplinkRequest.schemeOf("://x"))
    }

    @Test
    fun inMemoryReturnSchemeStore() {
        val store = InMemoryReturnSchemeStore()
        assertTrue(!store.contains("a"))
        store.add("a")
        assertTrue(store.contains("a"))
    }
}
