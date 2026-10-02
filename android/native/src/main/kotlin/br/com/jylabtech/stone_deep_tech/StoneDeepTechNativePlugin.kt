package br.com.jylabtech.stone_deep_tech

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.VisibleForTesting
import br.com.jylabtech.stone_deep_tech.BuildConfig
import br.com.jylabtech.stone_deep_tech.licence.CheckLicenceTech
import br.com.jylabtech.stone_deep_tech.licence.LicenceConfig
import br.com.jylabtech.stone_deep_tech.licence.LicenceResult
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class StoneDeepTechNativePlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val CHANNEL_NAME = "stone_deep_tech"
    private val TAG = "StoneDeepTechNative"

    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var licenceTech: CheckLicenceTech? = null
    private var licenceKey: String? = null
    private var licenceInternalKey: String? = null

    /** Validação da licença (bloqueante; roda em background). Substituível em testes. */
    @VisibleForTesting
    internal var licenceValidator: ((String, String) -> LicenceResult?)? = null

    // Uma thread só: validações em sequência reaproveitam o cache salvo pela anterior.
    // Criada sob demanda e encerrada em onDetachedFromEngine.
    private var licenceExecutor: ExecutorService? = null

    private fun licenceExecutor(): ExecutorService =
        licenceExecutor ?: Executors.newSingleThreadExecutor().also { licenceExecutor = it }

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** Executa a validação fora da main thread. Substituível em testes. */
    @VisibleForTesting
    internal var backgroundExecutor: (Runnable) -> Unit = { task -> licenceExecutor().execute(task) }

    /** Entrega o resultado na main thread. Substituível em testes. */
    @VisibleForTesting
    internal var mainThreadExecutor: (Runnable) -> Unit = { task -> mainHandler.post(task) }

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
        private const val LICENCE_ERROR_MESSAGE = "Sua licença não foi ativada."

        /** Extra que marca o intent de resposta da Stone já entregue ao Dart. */
        internal const val EXTRA_HANDLED = "br.com.jylabtech.stone_deep_tech.RESPONSE_HANDLED"

        /** Métodos que executam operações de negócio e exigem licença válida. */
        internal val GATED_METHODS = setOf("sendDeeplink", "sendCancel", "sendPrint", "sendReprint")
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME).also {
            it.setMethodCallHandler(this)
        }
        context = flutterPluginBinding.applicationContext
        context?.let {
            licenceTech = CheckLicenceTech(it)
            returnSchemes = SharedPrefsReturnSchemeStore(it)
        }
        initialiseLicenceDefaults()
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull rawResult: Result) {
        // Toda resposta passa por SafeResult: no máximo uma resposta por chamada.
        val result = SafeResult(rawResult, call.method)

        if (call.method == "configureLicence") {
            handleConfigureLicence(call, result)
            return
        }

        if (call.method !in GATED_METHODS) {
            result.notImplemented()
            return
        }

        if (!ensureLicenceConfigured(result)) {
            return
        }

        // Toda operação valida a licença antes de abrir o app da Stone (cache válido ou servidor).
        withValidLicence(result) {
            executeOperation(call, result)
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
     * Valida a licença em background e, na main thread, executa [onValid] somente se a licença
     * for válida e ativa. Caso contrário responde LICENCE_ERROR e não abre o app da Stone.
     */
    private fun withValidLicence(result: Result, onValid: () -> Unit) {
        val configuredLicenceKey = licenceKey?.trim().orEmpty()
        val configuredLicenceInternalKey = licenceInternalKey?.trim().orEmpty()
        val validator = licenceValidator ?: licenceTech?.let { tech ->
            { key: String, internalKey: String -> tech.validateLicence(key, internalKey) }
        }

        try {
            backgroundExecutor(Runnable {
                val licenceResult: LicenceResult? = try {
                    validator?.invoke(configuredLicenceKey, configuredLicenceInternalKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao validar licença", e)
                    null
                }
                val isValid = licenceResult?.result == 0

                mainThreadExecutor(Runnable {
                    if (isValid) {
                        onValid()
                    } else {
                        val message = licenceResult?.errorMessage?.takeIf { it.isNotBlank() }
                            ?: LICENCE_ERROR_MESSAGE
                        Log.e(TAG, "Erro na validação de licença: $message")
                        result.error("LICENCE_ERROR", message, null)
                    }
                })
            })
        } catch (e: Exception) {
            // Ex.: executor rejeitou a tarefa. Nunca deixa a Future do Dart sem resposta.
            Log.e(TAG, "Não foi possível agendar a validação de licença", e)
            result.error("LICENCE_ERROR", LICENCE_ERROR_MESSAGE, null)
        }
    }

    private fun handleConfigureLicence(call: MethodCall, result: Result) {
        val licenceKeyArgument = (call.argument<Any>("licenceKey") as? String)?.trim() ?: ""
        val licenceInternalKeyArgument = (call.argument<Any>("licenceInternalKey") as? String)?.trim() ?: ""

        if (licenceKeyArgument.isEmpty()) {
            result.error("INVALID_ARGUMENT", "licenceKey é obrigatório", null)
            return
        }

        licenceKey = licenceKeyArgument
        // Opcional: vazio usa o servidor oficial. Valores fora do servidor oficial são ignorados
        // na validação (LicenceConfig.resolveLicenceUrl).
        licenceInternalKey = licenceInternalKeyArgument.ifEmpty { LicenceConfig.LICENCE_CHECK_URL }

        Log.i(TAG, "Chaves de licença configuradas via Dart")
        result.success(true)

        // O Dart registra o handler de respostas antes de configurar a licença: entrega as pendentes.
        responseDispatcher.flushPending()
    }

    private fun ensureLicenceConfigured(result: Result): Boolean {
        val hasLicence = !licenceKey.isNullOrBlank()
        if (!hasLicence) {
            Log.e(TAG, "Licença não configurada. Chame configureLicence antes de usar o plugin.")
            result.error(
                "LICENCE_NOT_CONFIGURED",
                "Chaves de licença não configuradas. Chame configureLicence antes de utilizar o plugin.",
                null
            )
        }
        return hasLicence
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
        licenceTech = null
        licenceKey = null
        licenceInternalKey = null
        responseDispatcher.clear()
        licenceExecutor?.shutdown()
        licenceExecutor = null
    }

    private fun initialiseLicenceDefaults() {
        val defaultLicenceKey = BuildConfig.LICENCE_KEY
        val defaultLicenceInternalKey = BuildConfig.LICENCE_INTERNAL_KEY

        if (!defaultLicenceKey.isNullOrBlank()) {
            licenceKey = defaultLicenceKey
            licenceInternalKey = defaultLicenceInternalKey.ifBlank { LicenceConfig.LICENCE_CHECK_URL }
            Log.i(TAG, "Chaves de licença carregadas do BuildConfig")
        } else {
            Log.w(TAG, "Chaves de licença do BuildConfig estão vazias. Aguardando configuração via Dart.")
        }
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
