package br.com.jylabtech.stone_deep_tech

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Fachada fina sobre a abertura do app da Stone (substituída por fakes nos testes). */
fun interface StoneGateway {
    /** Abre o deeplink. Lança exceção se não for possível (ex.: app da Stone ausente). */
    fun open(request: DeeplinkRequest)
}

/** Implementação de produção: `Intent.ACTION_VIEW` com a URI montada por `Uri.Builder`. */
internal class IntentStoneGateway(private val contextProvider: () -> Context?) : StoneGateway {

    override fun open(request: DeeplinkRequest) {
        val context = contextProvider()
            ?: throw IllegalStateException("Plugin não está anexado ao Flutter engine.")

        val uriBuilder = Uri.Builder()
            .scheme(request.scheme)
            .authority(request.authority)
        for ((name, value) in request.queryParameters) {
            uriBuilder.appendQueryParameter(name, value)
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.data = uriBuilder.build()

        context.startActivity(intent)
        Log.v(TAG, "Deeplink sent: ${intent.data}")
    }

    private companion object {
        const val TAG = "StoneDeepTechNative"
    }
}
