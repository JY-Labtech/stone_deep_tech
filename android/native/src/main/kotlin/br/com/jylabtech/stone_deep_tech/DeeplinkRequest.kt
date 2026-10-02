package br.com.jylabtech.stone_deep_tech

import io.flutter.plugin.common.MethodCall

/**
 * Deeplink que será aberto no app da Stone, descrito sem tipos do Android (`Uri`/`Intent`)
 * para poder ser testado na JVM. A ordem de [queryParameters] é a ordem na URI.
 */
data class DeeplinkRequest(
    val scheme: String,
    val authority: String,
    val queryParameters: List<Pair<String, String>>,
    /** Scheme de retorno enviado à Stone (normalizado), usado para reconhecer a resposta. */
    val returnScheme: String? = null
) {
    /** Valor do primeiro parâmetro com esse nome, ou null. */
    fun param(name: String): String? = queryParameters.firstOrNull { it.first == name }?.second

    companion object {
        /** "MeuApp", "meuapp://" ou "meuapp://x" -> "meuapp"; vazio -> null. */
        fun normalizeScheme(value: String?): String? {
            val scheme = value?.trim()?.substringBefore("://")?.substringBefore(":")?.trim()?.lowercase()
            return scheme?.takeIf { it.isNotEmpty() }
        }

        /** Scheme de uma URI recebida ("meuapp://pay?..." -> "meuapp"); sem ":" -> null. */
        fun schemeOf(uri: String?): String? {
            if (uri == null || !uri.contains(':')) return null
            return normalizeScheme(uri.substringBefore(':'))
        }
    }

    /** Representação legível para logs (sem encoding). */
    override fun toString(): String {
        val query = queryParameters.joinToString("&") { "${it.first}=${it.second}" }
        return if (query.isEmpty()) "$scheme://$authority" else "$scheme://$authority?$query"
    }
}

/**
 * Converte as chamadas do MethodChannel em [DeeplinkRequest] no formato esperado pelo app da Stone.
 * Lógica pura (sem Android): a abertura fica em [StoneGateway].
 */
object DeeplinkRequests {
    private const val AMOUNT = "amount"
    private const val ORDER_ID = "order_id"
    private const val EDITABLE_AMOUNT = "editable_amount"
    private const val TRANSACTION_TYPE = "transaction_type"
    private const val INSTALLMENT_TYPE = "installment_type"
    private const val INSTALLMENT_COUNT = "installment_count"
    private const val RETURN_SCHEME = "return_scheme"
    private const val ATK = "atk"
    private const val TYPE_CUSTOMER = "TYPE_CUSTOMER"

    /** Monta o deeplink do método [MethodCall.method]. Lança [IllegalArgumentException] se inválido. */
    fun fromCall(call: MethodCall): DeeplinkRequest = when (call.method) {
        "sendDeeplink" -> payment(
            amount = call.longArg("amount"),
            editableAmount = call.boolArg("editableAmount"),
            transactionType = call.stringArg("transactionType"),
            installmentCount = call.longArg("installmentCount"),
            installmentType = call.stringArg("installmentType"),
            orderId = call.longArg("orderId"),
            returnScheme = call.stringArg("returnScheme")
        )
        "sendCancel" -> cancel(
            returnScheme = call.stringArg("returnScheme"),
            atk = call.stringArg("atk"),
            amount = call.longArg("amount"),
            editableAmount = call.boolArg("editableAmount")
        )
        "sendPrint" -> print(
            schemeReturn = call.stringArg("schemeReturn"),
            printableContent = call.stringArg("printableContent"),
            showFeedbackScreen = call.boolArg("showFeedbackScreen")
        )
        "sendReprint" -> reprint(
            schemeReturn = call.stringArg("schemeReturn"),
            atk = call.stringArg("atk"),
            typeCustomer = call.stringArg("typeCustomer"),
            showFeedbackScreen = call.boolArg("showFeedbackScreen")
        )
        else -> throw IllegalArgumentException("Método sem deeplink: ${call.method}")
    }

    fun payment(
        amount: Long?,
        editableAmount: Boolean?,
        transactionType: String?,
        installmentCount: Long?,
        installmentType: String?,
        orderId: Long?,
        returnScheme: String?
    ): DeeplinkRequest {
        val params = mutableListOf<Pair<String, String>>()
        returnScheme?.let { params += RETURN_SCHEME to it }
        params += EDITABLE_AMOUNT to if (editableAmount == true) "1" else "0"
        amount?.let { params += AMOUNT to it.toString() }
        transactionType?.let { params += TRANSACTION_TYPE to it }
        if (installmentType != null && transactionType == "CREDIT") {
            params += INSTALLMENT_TYPE to installmentType
        }
        installmentCount?.let { params += INSTALLMENT_COUNT to it.toString() }
        orderId?.let { params += ORDER_ID to it.toString() }
        return DeeplinkRequest("payment-app", "pay", params, DeeplinkRequest.normalizeScheme(returnScheme))
    }

    fun cancel(
        returnScheme: String?,
        atk: String?,
        amount: Long?,
        editableAmount: Boolean?
    ): DeeplinkRequest {
        val params = mutableListOf<Pair<String, String>>()
        returnScheme?.let { params += "returnscheme" to it }
        atk?.let { params += ATK to it }
        amount?.let { params += AMOUNT to it.toString() }
        params += EDITABLE_AMOUNT to if (editableAmount == true) "true" else "false"
        return DeeplinkRequest("cancel-app", "cancel", params, DeeplinkRequest.normalizeScheme(returnScheme))
    }

    fun print(
        schemeReturn: String?,
        printableContent: String?,
        showFeedbackScreen: Boolean?
    ): DeeplinkRequest {
        val params = mutableListOf<Pair<String, String>>()
        schemeReturn?.let { params += "SCHEME_RETURN" to it }
        printableContent?.let { params += "PRINTABLE_CONTENT" to it }
        params += "SHOW_FEEDBACK_SCREEN" to if (showFeedbackScreen == true) "true" else "false"
        return DeeplinkRequest("printer-app", "print", params, DeeplinkRequest.normalizeScheme(schemeReturn))
    }

    fun reprint(
        schemeReturn: String?,
        atk: String?,
        typeCustomer: String?,
        showFeedbackScreen: Boolean?
    ): DeeplinkRequest {
        val params = mutableListOf<Pair<String, String>>()
        schemeReturn?.let { params += "SCHEME_RETURN" to it }
        atk?.let { params += ATK to it }
        typeCustomer?.let { params += TYPE_CUSTOMER to it }
        params += "SHOW_FEEDBACK_SCREEN" to if (showFeedbackScreen == true) "true" else "false"
        return DeeplinkRequest("reprinter-app", "reprint", params, DeeplinkRequest.normalizeScheme(schemeReturn))
    }

    // O StandardMessageCodec entrega int do Dart como Integer (32 bits) ou Long (acima disso).
    // Ler como Number evita ClassCastException com valores grandes (ex.: orderId com timestamp).
    internal fun MethodCall.longArg(key: String): Long? = when (val value = argument<Any>(key)) {
        null -> null
        is Int -> value.toLong()
        is Long -> value
        is Short -> value.toLong()
        is Byte -> value.toLong()
        else -> throw IllegalArgumentException("$key deve ser um inteiro (recebido ${value.javaClass.simpleName})")
    }

    internal fun MethodCall.stringArg(key: String): String? = when (val value = argument<Any>(key)) {
        null -> null
        is String -> value
        else -> throw IllegalArgumentException("$key deve ser texto (recebido ${value.javaClass.simpleName})")
    }

    internal fun MethodCall.boolArg(key: String): Boolean? = when (val value = argument<Any>(key)) {
        null -> null
        is Boolean -> value
        else -> throw IllegalArgumentException("$key deve ser booleano (recebido ${value.javaClass.simpleName})")
    }
}
