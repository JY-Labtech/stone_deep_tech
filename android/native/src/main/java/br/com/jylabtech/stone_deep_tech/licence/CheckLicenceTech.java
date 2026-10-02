package br.com.jylabtech.stone_deep_tech.licence;

import android.content.Context;

import java.util.Date;

/**
 * Fluxo de validação da licença: primeiro o cache offline válido (sem rede); se não houver,
 * consulta o servidor oficial da JY Labtech. Métodos bloqueantes: chame fora da main thread.
 */
public class CheckLicenceTech {

    /** Relógio injetável (testes). */
    public interface Clock {
        Date now();
    }

    private final LicenceRemote licenceRemote;
    private final LicenceStorage licenceStorage;
    private final Clock clock;

    public CheckLicenceTech(Context context) {
        this(new LicenceProvider(context), new EncryptedLicenceStorage(context), Date::new);
    }

    public CheckLicenceTech(LicenceRemote licenceRemote, LicenceStorage licenceStorage, Clock clock) {
        this.licenceRemote = licenceRemote;
        this.licenceStorage = licenceStorage;
        this.clock = clock;
    }

    public LicenceResult validateLicence(String licenceKey, String licenceInternalKey) {
        try {
            if (licenceKey == null || licenceKey.trim().isEmpty()) {
                return LicenceRules.error("Chave de licença não configurada.");
            }

            String resolvedUrl = LicenceConfig.resolveLicenceUrl(licenceInternalKey);
            Date now = clock.now();

            CheckLicenceEntity cached = licenceStorage.load();
            if (cached != null) {
                if (LicenceRules.isCacheUsable(cached, licenceKey, resolvedUrl, now)) {
                    LicenceResult result = new LicenceResult();
                    result.setMethod("licence");
                    result.setResult(LicenceRules.RESULT_SUCCESS);
                    result.setLicenceEntity(cached);
                    return result;
                }
                licenceStorage.delete();
            }

            LicenceResult remote = licenceRemote.checkLicence(resolvedUrl, licenceKey);
            if (remote == null) {
                return LicenceRules.error("Erro ao validar licença.");
            }

            CheckLicenceEntity entity = remote.getLicenceEntity();
            if (remote.isSuccess() && LicenceRules.isLicenceUsable(entity, now)) {
                entity.setLicenceInternalKey(resolvedUrl);
                entity.setSavedLocalDatabaseAt(now);
                licenceStorage.save(entity);
                return remote;
            }

            if (remote.isSuccess()) {
                // Defesa extra: nunca devolve sucesso para licença não utilizável.
                return LicenceRules.error(LicenceRules.MESSAGE_LICENCE_NOT_ACTIVE);
            }
            if (remote.getErrorMessage() == null || remote.getErrorMessage().isEmpty()) {
                remote.setErrorMessage("Erro ao validar licença.");
            }
            return remote;
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return LicenceRules.error("Erro ao validar licença: " + detail);
        }
    }

    /** Indica se existe uma licença offline utilizável e salva há no máximo 7 dias (sem rede). */
    public boolean validateExistsLicence() {
        try {
            Date now = clock.now();
            CheckLicenceEntity cached = licenceStorage.load();
            if (cached == null || !LicenceRules.isLicenceUsable(cached, now)) return false;
            Date savedAt = cached.getSavedLocalDatabaseAt();
            if (savedAt == null) return false;
            long age = now.getTime() - savedAt.getTime();
            if (age > LicenceRules.MAX_CACHE_AGE_MILLIS || age < -LicenceRules.CLOCK_SKEW_TOLERANCE_MILLIS) {
                licenceStorage.delete();
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
