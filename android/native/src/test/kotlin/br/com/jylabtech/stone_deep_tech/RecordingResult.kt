package br.com.jylabtech.stone_deep_tech

import io.flutter.plugin.common.MethodChannel

/** Result que registra todas as respostas (para checar resposta única). */
class RecordingResult : MethodChannel.Result {
    var successValue: Any? = null
    var successCalled = false
    var errorCode: String? = null
    var errorMessage: String? = null
    var notImplementedCalled = false
    var replies = 0

    override fun success(result: Any?) {
        replies++
        successCalled = true
        successValue = result
    }

    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
        replies++
        this.errorCode = errorCode
        this.errorMessage = errorMessage
    }

    override fun notImplemented() {
        replies++
        notImplementedCalled = true
    }
}
