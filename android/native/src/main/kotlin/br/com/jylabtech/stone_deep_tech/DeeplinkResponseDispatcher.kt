package br.com.jylabtech.stone_deep_tech

import android.util.Log
import io.flutter.plugin.common.MethodChannel

/**
 * Entrega ao Dart as respostas da Stone (`onDeeplinkResponse`/`onDeeplinkError`).
 *
 * Se o Dart ainda não registrou o handler (app aberto a frio pelo deeplink de retorno, antes de
 * `initDeeplink`), a chamada volta como `notImplemented` e a resposta fica pendente até [flushPending],
 * chamado quando o Dart configura a licença (logo depois de registrar o handler). Usar só na main thread.
 */
internal class DeeplinkResponseDispatcher(
    private val sender: (method: String, arguments: Any?, callback: MethodChannel.Result) -> Unit
) {
    private val pending = ArrayDeque<Pair<String, String>>()

    val pendingCount: Int get() = pending.size

    fun dispatch(method: String, payload: String) {
        try {
            sender(method, payload, object : MethodChannel.Result {
                override fun success(result: Any?) = Unit

                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    Log.e(TAG, "Handler Dart falhou em $method: $errorCode $errorMessage")
                }

                override fun notImplemented() {
                    enqueue(method, payload)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao entregar $method ao Dart", e)
            enqueue(method, payload)
        }
    }

    /** Reenvia as respostas que ainda não tinham handler Dart. */
    fun flushPending() {
        if (pending.isEmpty()) return
        val items = pending.toList()
        pending.clear()
        for ((method, payload) in items) dispatch(method, payload)
    }

    fun clear() = pending.clear()

    private fun enqueue(method: String, payload: String) {
        while (pending.size >= MAX_PENDING) pending.removeFirst()
        pending.addLast(method to payload)
        Log.w(TAG, "Sem handler Dart para $method; resposta guardada até initDeeplink")
    }

    companion object {
        private const val TAG = "StoneDeepTechNative"
        const val MAX_PENDING = 10
    }
}
