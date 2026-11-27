package br.com.jylabtech.stone_deep_tech

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.VisibleForTesting
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class StoneDeepTechNativePlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val CHANNEL_NAME = "stone_deep_tech"
    private val TAG = "StoneDeepTechNative"

    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activityBinding: ActivityPluginBinding? = null

    /** Abre o app da Stone. Substituível em testes. */
    @VisibleForTesting
    internal var gateway: StoneGateway = IntentStoneGateway { context }

    /** Envia uma chamada ao Dart (padrão: MethodChannel). Substituível em testes. */
    @VisibleForTesting
    internal var responseSender: (String, Any?, Result) -> Unit = { method, arguments, callback ->
        val currentChannel = channel
        if (currentChannel == null) callback.notImplemented()
        else currentChannel.invokeMethod(method, arguments, callback)
    }

    @VisibleForTesting
    internal val responseDispatcher = DeeplinkResponseDispatcher { method, arguments, callback ->
        responseSender(method, arguments, callback)
    }

    /** Schemes de retorno enviados à Stone (persistido quando há contexto). Substituível em testes. */
    @VisibleForTesting
    internal var returnSchemes: ReturnSchemeStore = InMemoryReturnSchemeStore()

    // Só consome (true) intents que são resposta da Stone; os demais seguem para o app.
    private val newIntentListener = PluginRegistry.NewIntentListener { intent -> handleIntent(intent) }

    companion object {
        /** Extra que marca o intent de resposta da Stone já entregue ao Dart. */
        internal const val EXTRA_HANDLED = "br.com.jylabtech.stone_deep_tech.RESPONSE_HANDLED"

        /** Chamado pelo Dart logo depois de registrar o handler: entrega as respostas pendentes. */
        internal const val INIT_METHOD = "initDeeplink"

        /** Métodos que abrem o app da Stone. */
        internal val OPERATION_METHODS = setOf("sendDeeplink", "sendCancel", "sendPrint", "sendReprint")
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME).also {
            it.setMethodCallHandler(this)
        }
        context = flutterPluginBinding.applicationContext
        context?.let { returnSchemes = SharedPrefsReturnSchemeStore(it) }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull rawResult: Result) {
        // Toda resposta passa por SafeResult: no máximo uma resposta por chamada.
        val result = SafeResult(rawResult, call.method)

        when (call.method) {
            INIT_METHOD -> handleInit(result)
            in OPERATION_METHODS -> executeOperation(call, result)
            else -> result.notImplemented()
        }
    }

    private fun executeOperation(call: MethodCall, result: Result) {
        try {
            val request = DeeplinkRequests.fromCall(call)
            // Registra antes de abrir: a resposta da Stone volta com esse scheme.
            request.returnScheme?.let { returnSchemes.add(it) }
            gateway.open(request)
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar ${call.method}", e)
            result.error("ERROR", e.message ?: e.javaClass.simpleName, null)
        }
    }

    /**
     * O Dart registra o handler de respostas e chama `initDeeplink`: entrega as respostas da Stone
     * que chegaram antes (app aberto a frio pelo deeplink de retorno).
     */
    private fun handleInit(result: Result) {
        result.success(true)
        responseDispatcher.flushPending()
    }

    private fun handleIntent(intent: Intent): Boolean = handleIntent(
        action = intent.action,
        data = intent.dataString,
        alreadyHandled = { intent.getBooleanExtra(EXTRA_HANDLED, false) },
        markHandled = { intent.putExtra(EXTRA_HANDLED, true) }
    )

    /**
     * Trata um intent recebido (abertura ou onNewIntent). Retorna true (consumido) só se for resposta da
     * Stone: `ACTION_VIEW` com o scheme de retorno enviado à Stone e ainda não entregue. O intent é
     * marcado com [EXTRA_HANDLED]; se a Activity for recriada com o mesmo intent, ele não é reentregue.
     */
    @VisibleForTesting
    internal fun handleIntent(
        action: String?,
        data: String?,
        alreadyHandled: () -> Boolean,
        markHandled: () -> Unit
    ): Boolean {
        if (action != Intent.ACTION_VIEW) return false
        val scheme = DeeplinkRequest.schemeOf(data) ?: return false
        if (!returnSchemes.contains(scheme)) return false // deeplink do próprio app: não consome
        if (alreadyHandled()) return true                  // já entregue (Activity recriada)
        try {
            markHandled()
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível marcar o intent como entregue", e)
        }
        onDeeplinkResponseData(data)
        return true
    }

    /** Entrega ao Dart a URI de retorno da Stone (ignora intents sem dados). */
    @VisibleForTesting
    internal fun onDeeplinkResponseData(data: String?) {
        if (data.isNullOrEmpty()) return
        Log.i(TAG, "Deeplink response received: $data")
        responseDispatcher.dispatch("onDeeplinkResponse", data)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        context = null
        responseDispatcher.clear()
    }

    private fun attachActivity(binding: ActivityPluginBinding, handleLaunchIntent: Boolean) {
        activityBinding?.removeOnNewIntentListener(newIntentListener)
        activityBinding = binding
        binding.addOnNewIntentListener(newIntentListener)

        if (!handleLaunchIntent) return
        binding.activity.intent?.let { handleIntent(it) }
    }

    private fun detachActivity() {
        activityBinding?.removeOnNewIntentListener(newIntentListener)
        activityBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        attachActivity(binding, handleLaunchIntent = true)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        // Mudança de configuração (ex.: rotação): o intent de abertura já foi entregue.
        attachActivity(binding, handleLaunchIntent = false)
    }

    override fun onDetachedFromActivity() {
        detachActivity()
    }
}
