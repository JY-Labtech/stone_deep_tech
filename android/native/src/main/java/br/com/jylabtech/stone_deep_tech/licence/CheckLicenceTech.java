package br.com.jylabtech.stone_deep_tech.licence;

import android.content.Context;

import java.util.Date;

/**
 * Fluxo de validação da licença conforme o contrato de licenças do jylabtech_core:
 *
 * <ul>
 *   <li>A primeira validação de cada processo (abertura do app) sempre consulta o servidor.</li>
 *   <li>Depois disso, com cache válido, usa o cache e consulta o servidor de novo a cada
 *       {@link LicenceRules#REVALIDATE_INTERVAL_MILLIS} (4h).</li>
 *   <li>Recusa definitiva do servidor (cliente bloqueado, licença vencida/cancelada/suspensa, não
 *       encontrada, aparelho desativado...) apaga o cache e bloqueia na hora.</li>
 *   <li>Falha de rede, timeout, 408/429/5xx ou corpo ilegível usa o cache válido (salvo há no
 *       máximo 72h); sem cache, bloqueia. Após uma falha, espera
 *       {@link LicenceRules#RETRY_AFTER_FAILURE_MILLIS} para tentar de novo, para não travar cada
 *       operação offline no timeout.</li>
 * </ul>
 *
 * Métodos bloqueantes: chame fora da main thread.
 */
public class CheckLicenceTech {

    /** Relógio injetável (testes). */
    public interface Clock {
        Date now();
    }

    private final LicenceRemote licenceRemote;
    private final LicenceStorage licenceStorage;
    private final Clock clock;

    // Estado desta instância/processo (não persistido): reiniciar o app força nova consulta ao servidor.
    // volatile: validateExistsLicence lê sem lock (pode ser chamado na main thread).
    private volatile boolean checkedOnlineInThisProcess;
    private volatile Date lastOnlineFailureAt;

    public CheckLicenceTech(Context context) {
        this(new LicenceProvider(context), new EncryptedLicenceStorage(context), Date::new);
    }

    public CheckLicenceTech(LicenceRemote licenceRemote, LicenceStorage licenceStorage, Clock clock) {
        this.licenceRemote = licenceRemote;
        this.licenceStorage = licenceStorage;
        this.clock = clock;
    }

    public synchronized LicenceResult validateLicence(String licenceKey, String licenceInternalKey) {
        try {
            if (licenceKey == null || licenceKey.trim().isEmpty()) {
                return LicenceRules.error("Chave de licença não configurada.");
            }

            String resolvedUrl = LicenceConfig.resolveLicenceUrl(licenceInternalKey);
            Date now = clock.now();

            CheckLicenceEntity cached = licenceStorage.load();
            if (cached != null && !LicenceRules.isCacheUsable(cached, licenceKey, resolvedUrl, now)) {
                licenceStorage.delete();
                cached = null;
            }
            if (cached != null && !LicenceRules.shouldRevalidate(cached, checkedOnlineInThisProcess, now)) {
                return cacheSuccess(cached);
            }
            if (cached != null && recentlyFailedOnline(now)) {
                return cacheSuccess(cached);
            }

            LicenceResult remote;
            try {
                remote = licenceRemote.checkLicence(resolvedUrl, licenceKey);
            } catch (Exception e) {
                return onlineFailure(cached, now, "Erro ao validar licença: " + describe(e));
            }
            if (remote == null) {
                return onlineFailure(cached, now, "Erro ao validar licença.");
            }

            CheckLicenceEntity entity = remote.getLicenceEntity();
            if (remote.isSuccess() && LicenceRules.isLicenceUsable(entity, now)) {
                entity.setLicenceInternalKey(resolvedUrl);
                entity.setSavedLocalDatabaseAt(now);
                licenceStorage.save(entity);
                checkedOnlineInThisProcess = true;
                lastOnlineFailureAt = null;
                return remote;
            }

            if (remote.isSuccess() || remote.isRejectedByServer()) {
                // Recusa definitiva: o cache não pode mais liberar o uso.
                licenceStorage.delete();
                checkedOnlineInThisProcess = true;
                lastOnlineFailureAt = null;
                // Defesa extra: nunca devolve sucesso para licença não utilizável.
                LicenceResult rejected = LicenceRules.error(remote.isSuccess()
                        ? LicenceRules.MESSAGE_LICENCE_NOT_ACTIVE
                        : messageOrDefault(remote.getErrorMessage()));
                rejected.setRejectedByServer(true);
                return rejected;
            }
            return onlineFailure(cached, now, messageOrDefault(remote.getErrorMessage()));
        } catch (Exception e) {
            return LicenceRules.error("Erro ao validar licença: " + describe(e));
        }
    }

    /**
     * Atalho só-cache (sem rede): true só quando {@link #validateLicence} também liberaria sem
     * consultar o servidor agora, isto é, licença offline utilizável salva há no máximo 72h E (já
     * validada online nesta instância há menos de 4h OU servidor fora do ar há menos de 5 min).
     * Não usa lock: pode ser chamado na main thread enquanto {@link #validateLicence} roda.
     */
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
            return !LicenceRules.shouldRevalidate(cached, checkedOnlineInThisProcess, now)
                    || recentlyFailedOnline(now);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean recentlyFailedOnline(Date now) {
        Date failedAt = lastOnlineFailureAt;
        if (failedAt == null) return false;
        long elapsed = now.getTime() - failedAt.getTime();
        return elapsed >= 0 && elapsed < LicenceRules.RETRY_AFTER_FAILURE_MILLIS;
    }

    /** Servidor inacessível: usa o cache válido (até 72h) ou bloqueia se não houver. */
    private LicenceResult onlineFailure(CheckLicenceEntity cached, Date now, String message) {
        lastOnlineFailureAt = now;
        if (cached != null) {
            return cacheSuccess(cached);
        }
        return LicenceRules.error(message);
    }

    private static LicenceResult cacheSuccess(CheckLicenceEntity cached) {
        LicenceResult result = new LicenceResult();
        result.setMethod("licence");
        result.setResult(LicenceRules.RESULT_SUCCESS);
        result.setLicenceEntity(cached);
        return result;
    }

    private static String messageOrDefault(String message) {
        return message == null || message.isEmpty() ? "Erro ao validar licença." : message;
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
