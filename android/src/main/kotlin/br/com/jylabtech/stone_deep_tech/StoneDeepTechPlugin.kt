package br.com.jylabtech.stone_deep_tech

import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class StoneDeepTechPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {

    companion object {
        private const val TAG = "StoneDeepTechPlugin"
        private const val DELEGATE_CLASS = "br.com.jylabtech.stone_deep_tech.StoneDeepTechNativePlugin"
    }

    private var delegateInstance: Any? = null
    private var flutterPluginDelegate: FlutterPlugin? = null
    private var methodCallDelegate: MethodChannel.MethodCallHandler? = null
    private var activityAwareDelegate: ActivityAware? = null

    private fun ensureDelegate(): Any {
        delegateInstance?.let { return it }

        return try {
            val clazz = Class.forName(DELEGATE_CLASS)
            val instance = clazz.getDeclaredConstructor().newInstance()
            delegateInstance = instance
            flutterPluginDelegate = instance as? FlutterPlugin
            methodCallDelegate = instance as? MethodChannel.MethodCallHandler
            activityAwareDelegate = instance as? ActivityAware
            instance
        } catch (ex: ClassNotFoundException) {
            throw IllegalStateException(
                "Implementação nativa não encontrada. Execute scripts/build_android_aar.sh para gerar o artefato.",
                ex
            )
        } catch (ex: Exception) {
            throw IllegalStateException("Falha ao inicializar implementação nativa", ex)
        }
    }

    private fun requireFlutterPluginDelegate(): FlutterPlugin {
        ensureDelegate()
        return flutterPluginDelegate
            ?: throw IllegalStateException("Implementação nativa inválida: não implementa FlutterPlugin")
    }

    private fun requireMethodCallDelegate(): MethodChannel.MethodCallHandler {
        ensureDelegate()
        return methodCallDelegate
            ?: throw IllegalStateException("Implementação nativa inválida: não implementa MethodCallHandler")
    }

    private fun getActivityAwareDelegate(): ActivityAware? {
        ensureDelegate()
        return activityAwareDelegate
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        try {
            requireFlutterPluginDelegate().onAttachedToEngine(binding)
        } catch (ex: IllegalStateException) {
            Log.e(TAG, "Erro ao anexar plugin nativo", ex)
            throw ex
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginDelegate?.onDetachedFromEngine(binding)
        methodCallDelegate = null
        activityAwareDelegate = null
        flutterPluginDelegate = null
        delegateInstance = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            requireMethodCallDelegate().onMethodCall(call, result)
        } catch (ex: IllegalStateException) {
            Log.e(TAG, "Implementação nativa indisponível", ex)
            result.error(
                "stone_deep_tech_missing_binary",
                "Implementação nativa não encontrada. Gere o binário Android.",
                ex.message
            )
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        getActivityAwareDelegate()?.onAttachedToActivity(binding)
            ?: Log.w(TAG, "Implementação nativa não fornece ActivityAware")
    }

    override fun onDetachedFromActivityForConfigChanges() {
        getActivityAwareDelegate()?.onDetachedFromActivityForConfigChanges()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        getActivityAwareDelegate()?.onReattachedToActivityForConfigChanges(binding)
    }

    override fun onDetachedFromActivity() {
        getActivityAwareDelegate()?.onDetachedFromActivity()
    }
}
