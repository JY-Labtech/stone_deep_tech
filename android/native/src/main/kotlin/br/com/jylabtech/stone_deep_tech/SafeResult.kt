package br.com.jylabtech.stone_deep_tech

import android.util.Log
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Garante uma única resposta por chamada do MethodChannel. Uma segunda resposta
 * (que derrubaria o app com "Reply already submitted") é ignorada e logada.
 */
internal class SafeResult(
    private val delegate: MethodChannel.Result,
    private val method: String
) : MethodChannel.Result {

    private val replied = AtomicBoolean(false)

    val hasReplied: Boolean get() = replied.get()

    private fun claim(kind: String): Boolean {
        if (replied.compareAndSet(false, true)) return true
        Log.w(TAG, "Resposta duplicada ignorada ($kind) em $method")
        return false
    }

    override fun success(result: Any?) {
        if (claim("success")) delegate.success(result)
    }

    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
        if (claim("error $errorCode")) delegate.error(errorCode, errorMessage, errorDetails)
    }

    override fun notImplemented() {
        if (claim("notImplemented")) delegate.notImplemented()
    }

    private companion object {
        const val TAG = "StoneDeepTechNative"
    }
}
