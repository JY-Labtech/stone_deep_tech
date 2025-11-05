package br.com.jylabtech.stone_deep_tech

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.NonNull
import br.com.jylabtech.stone_deep_tech.BuildConfig
import br.com.jylabtech.stone_deep_tech.licence.CheckLicenceTech
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class StoneDeepTechPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private val CHANNEL_NAME = "stone_deep_tech"
    private val TAG = "StoneDeepTechPlugin"
    
    private lateinit var channel: MethodChannel
    private var context: Context? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var licenceTech: CheckLicenceTech? = null

    companion object {
        private const val AMOUNT = "amount"
        private const val ORDER_ID = "order_id"
        private const val EDITABLE_AMOUNT = "editable_amount"
        private const val TRANSACTION_TYPE = "transaction_type"
        private const val INSTALLMENT_TYPE = "installment_type"
        private const val INSTALLMENT_COUNT = "installment_count"
        private const val RETURN_SCHEME = "return_scheme"
        private const val ATK = "atk"
        private const val TYPE_CUSTOMER = "TYPE_CUSTOMER"
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        context?.let {
            licenceTech = CheckLicenceTech(it)
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        // Validar licença antes de permitir qualquer operação
        if (!isValidLicence()) {
            result.error("LICENCE_ERROR", "Sua licença não foi ativada.", null)
            return
        }

        when (call.method) {
            "sendDeeplink" -> {
                try {
                    sendDeeplink(
                        call.argument<Int>("amount"),
                        call.argument<Boolean>("editableAmount"),
                        call.argument<String>("transactionType"),
                        call.argument<Int>("installmentCount"),
                        call.argument<String>("installmentType"),
                        call.argument<Int>("orderId"),
                        call.argument<String>("returnScheme")
                    )
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            "sendCancel" -> {
                try {
                    sendCancel(
                        call.argument<String>("returnScheme"),
                        call.argument<String>("atk"),
                        call.argument<Int>("amount"),
                        call.argument<Boolean>("editableAmount")
                    )
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            "sendPrint" -> {
                try {
                    sendPrint(
                        call.argument<String>("schemeReturn"),
                        call.argument<String>("printableContent"),
                        call.argument<Boolean>("showFeedbackScreen")
                    )
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            "sendReprint" -> {
                try {
                    sendReprint(
                        call.argument<String>("schemeReturn"),
                        call.argument<String>("atk"),
                        call.argument<String>("typeCustomer"),
                        call.argument<Boolean>("showFeedbackScreen")
                    )
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun sendDeeplink(
        amount: Int?,
        editableAmount: Boolean?,
        transactionType: String?,
        installmentCount: Int?,
        installmentType: String?,
        orderId: Int?,
        returnScheme: String?
    ) {
        val uriBuilder = Uri.Builder()
        uriBuilder.authority("pay")
        uriBuilder.scheme("payment-app")
        
        if (returnScheme != null) {
            uriBuilder.appendQueryParameter(RETURN_SCHEME, returnScheme)
        }
        
        uriBuilder.appendQueryParameter(
            EDITABLE_AMOUNT, 
            if (editableAmount == true) "1" else "0"
        )

        if (amount != null) {
            uriBuilder.appendQueryParameter(AMOUNT, amount.toLong().toString())
        }

        if (transactionType != null) {
            uriBuilder.appendQueryParameter(TRANSACTION_TYPE, transactionType)
        }

        // installmentType só deve ser enviado se transactionType for CREDIT
        if (installmentType != null && transactionType == "CREDIT") {
            uriBuilder.appendQueryParameter(INSTALLMENT_TYPE, installmentType)
        }

        if (installmentCount != null) {
            uriBuilder.appendQueryParameter(INSTALLMENT_COUNT, installmentCount.toString())
        }

        if (orderId != null) {
            uriBuilder.appendQueryParameter(ORDER_ID, orderId.toLong().toString())
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.data = uriBuilder.build()
        
        context?.startActivity(intent)
        
        Log.v(TAG, "Deeplink sent: ${intent.data}")
    }

    private fun sendCancel(
        returnScheme: String?,
        atk: String?,
        amount: Int?,
        editableAmount: Boolean?
    ) {
        val uriBuilder = Uri.Builder()
        uriBuilder.authority("cancel")
        uriBuilder.scheme("cancel-app")
        
        if (returnScheme != null) {
            uriBuilder.appendQueryParameter("returnscheme", returnScheme)
        }
        
        if (atk != null) {
            uriBuilder.appendQueryParameter(ATK, atk)
        }

        if (amount != null) {
            uriBuilder.appendQueryParameter(AMOUNT, amount.toLong().toString())
        }

        uriBuilder.appendQueryParameter(
            EDITABLE_AMOUNT,
            if (editableAmount == true) "true" else "false"
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.data = uriBuilder.build()
        
        context?.startActivity(intent)
        
        Log.v(TAG, "Cancel deeplink sent: ${intent.data}")
    }

    private fun sendPrint(
        schemeReturn: String?,
        printableContent: String?,
        showFeedbackScreen: Boolean?
    ) {
        val uriBuilder = Uri.Builder()
        uriBuilder.authority("print")
        uriBuilder.scheme("printer-app")
        
        if (schemeReturn != null) {
            uriBuilder.appendQueryParameter("SCHEME_RETURN", schemeReturn)
        }
        
        if (printableContent != null) {
            uriBuilder.appendQueryParameter("PRINTABLE_CONTENT", printableContent)
        }

        uriBuilder.appendQueryParameter(
            "SHOW_FEEDBACK_SCREEN",
            if (showFeedbackScreen == true) "true" else "false"
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.data = uriBuilder.build()
        
        context?.startActivity(intent)
        
        Log.v(TAG, "Print deeplink sent: ${intent.data}")
    }

    private fun sendReprint(
        schemeReturn: String?,
        atk: String?,
        typeCustomer: String?,
        showFeedbackScreen: Boolean?
    ) {
        val uriBuilder = Uri.Builder()
        uriBuilder.authority("reprint")
        uriBuilder.scheme("reprinter-app")

        if (schemeReturn != null) {
            uriBuilder.appendQueryParameter("SCHEME_RETURN", schemeReturn)
        }

        if (atk != null) {
            uriBuilder.appendQueryParameter(ATK, atk)
        }

        if (typeCustomer != null) {
            uriBuilder.appendQueryParameter(TYPE_CUSTOMER, typeCustomer)
        }

        uriBuilder.appendQueryParameter(
            "SHOW_FEEDBACK_SCREEN",
            if (showFeedbackScreen == true) "true" else "false"
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.data = uriBuilder.build()

        context?.startActivity(intent)

        Log.v(TAG, "Reprint deeplink sent: ${intent.data}")
    }

    private fun handleDeepLinkResponse(intent: Intent?) {
        try {
            val data = intent?.data
            if (data != null) {
                Log.i(TAG, "Deeplink response received: $data")
                // Send response back to Flutter
                channel.invokeMethod("onDeeplinkResponse", data.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling deeplink response", e)
            channel.invokeMethod("onDeeplinkError", e.message ?: "Unknown error")
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        context = null
        licenceTech = null
    }

    /**
     * Valida a licença antes de permitir uso das funcionalidades
     */
    private fun isValidLicence(): Boolean {
        return try {
            val licenceKeyLocal = BuildConfig.LICENCE_KEY
            val licenceInternalKey = BuildConfig.LICENCE_INTERNAL_KEY
            
            Log.i(TAG, "**Licença local: $licenceKeyLocal")
            
            if (licenceKeyLocal.isEmpty() || licenceInternalKey.isEmpty()) {
                Log.e(TAG, "Licença não configurada")
                return false
            }
            
            val resultLicence = licenceTech?.validateLicence(licenceKeyLocal, licenceInternalKey)
            val isValid = resultLicence?.result == 0
            
            Log.i(TAG, "*resultLicence: $isValid")
            
            if (!isValid && resultLicence != null) {
                Log.e(TAG, "Erro na validação de licença: ${resultLicence.errorMessage}")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao validar licença", e)
            false
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        // Register listener for onNewIntent
        binding.addOnNewIntentListener { intent ->
            if (intent.action == Intent.ACTION_VIEW) {
                handleDeepLinkResponse(intent)
                true
            } else {
                false
            }
        }
        // Handle initial intent if app was opened via deeplink
        binding.activity.intent?.let { intent ->
            if (intent.action == Intent.ACTION_VIEW) {
                handleDeepLinkResponse(intent)
            }
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        // Re-register listener for onNewIntent
        binding.addOnNewIntentListener { intent ->
            if (intent.action == Intent.ACTION_VIEW) {
                handleDeepLinkResponse(intent)
                true
            } else {
                false
            }
        }
        // Handle intent when activity is recreated
        binding.activity.intent?.let { intent ->
            if (intent.action == Intent.ACTION_VIEW) {
                handleDeepLinkResponse(intent)
            }
        }
    }

    override fun onDetachedFromActivity() {
        activityBinding = null
    }
}
