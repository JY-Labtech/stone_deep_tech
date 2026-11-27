package br.com.jylabtech.stone_deep_tech

import android.content.Context
import android.util.Log

/**
 * Schemes de retorno já enviados à Stone (`return_scheme`, `returnscheme`, `SCHEME_RETURN`).
 * Só intents `ACTION_VIEW` com um desses schemes são tratados como resposta da Stone.
 */
interface ReturnSchemeStore {
    fun contains(scheme: String): Boolean

    fun add(scheme: String)
}

/** Em memória (testes, ou plugin sem contexto). */
class InMemoryReturnSchemeStore : ReturnSchemeStore {
    private val schemes = mutableSetOf<String>()

    @Synchronized
    override fun contains(scheme: String) = scheme in schemes

    @Synchronized
    override fun add(scheme: String) {
        schemes += scheme
    }
}

/**
 * Persistido em SharedPreferences próprio (não sensível: só o nome do scheme), para reconhecer a
 * resposta mesmo se o processo morrer enquanto o app da Stone está aberto.
 */
internal class SharedPrefsReturnSchemeStore(context: Context) : ReturnSchemeStore {
    private val appContext = context.applicationContext ?: context
    private var cache: MutableSet<String>? = null

    private fun schemes(): MutableSet<String> = cache ?: try {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_SCHEMES, emptySet())?.toMutableSet() ?: mutableSetOf()
    } catch (e: Exception) {
        Log.e(TAG, "Erro ao ler schemes de retorno", e)
        mutableSetOf()
    }.also { cache = it }

    @Synchronized
    override fun contains(scheme: String) = scheme in schemes()

    @Synchronized
    override fun add(scheme: String) {
        val current = schemes()
        if (!current.add(scheme)) return
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_SCHEMES, HashSet(current)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar scheme de retorno", e)
        }
    }

    private companion object {
        const val TAG = "StoneDeepTechNative"
        const val PREFS_NAME = "stone_deep_tech_return"
        const val KEY_SCHEMES = "return_schemes"
    }
}
