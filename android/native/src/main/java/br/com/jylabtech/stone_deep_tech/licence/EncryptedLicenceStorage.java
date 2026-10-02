package br.com.jylabtech.stone_deep_tech.licence;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/** Cache da licença em EncryptedSharedPreferences (mesmos nomes de prefs/chave das versões anteriores). */
public class EncryptedLicenceStorage implements LicenceStorage {

    private static final String TAG = "CheckLicenceTech";
    static final String PREFS_NAME = "licence_prefs_encrypted";
    static final String KEY_JSON = "licence_entity";

    private final Context context;

    /**
     * Criar MasterKey + EncryptedSharedPreferences é caro (Keystore + Tink); antes era feito a cada
     * load/save/delete (até 3 vezes por validação). Agora é criado uma vez e reaproveitado. Se a
     * criação falhar, nada é guardado e a próxima chamada tenta de novo.
     */
    private SharedPreferences cachedPrefs;

    public EncryptedLicenceStorage(Context context) {
        this.context = context;
    }

    private synchronized SharedPreferences prefs() throws Exception {
        if (cachedPrefs == null) {
            cachedPrefs = createPrefs();
        }
        return cachedPrefs;
    }

    private SharedPreferences createPrefs() throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    @Override
    public CheckLicenceEntity load() {
        try {
            String savedJson = prefs().getString(KEY_JSON, null);
            if (savedJson != null) {
                return CheckLicenceEntity.fromJson(savedJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao recuperar licença criptografada", e);
        }
        return null;
    }

    @Override
    public void save(CheckLicenceEntity entity) {
        try {
            String json = entity.toJson();
            if (json != null) {
                prefs().edit().putString(KEY_JSON, json).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar licença criptografada", e);
        }
    }

    @Override
    public void delete() {
        try {
            prefs().edit().remove(KEY_JSON).apply();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao apagar licença criptografada", e);
        }
    }
}
