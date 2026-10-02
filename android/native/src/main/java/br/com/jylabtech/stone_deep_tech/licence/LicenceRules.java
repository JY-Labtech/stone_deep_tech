package br.com.jylabtech.stone_deep_tech.licence;

import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Regras de validação da licença, centralizadas e sem dependências do Android
 * (testáveis em JVM).
 */
public final class LicenceRules {

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_ERROR = 999999;

    /** Tempo máximo que a licença salva offline vale sem nova consulta ao servidor. */
    public static final long MAX_CACHE_AGE_MILLIS = TimeUnit.DAYS.toMillis(7);
    /** Tolerância para relógio do aparelho adiantado/atrasado em relação ao cache. */
    public static final long CLOCK_SKEW_TOLERANCE_MILLIS = TimeUnit.MINUTES.toMillis(5);

    public static final String MESSAGE_LICENCE_NOT_ACTIVE =
            "Licença inválida, vencida, suspensa ou cancelada.";
    public static final String MESSAGE_INVALID_RESPONSE =
            "Resposta inválida do servidor de licenças.";

    private LicenceRules() {}

    /** A licença só pode ser usada se estiver dentro da validade e ativa. */
    public static boolean isLicenceUsable(CheckLicenceEntity entity, Date now) {
        if (entity == null || now == null) return false;
        Date expiredAt = entity.getExpiredAt();
        if (expiredAt == null || !expiredAt.after(now)) return false;
        return !entity.isExpired() && !entity.isInactive() && !entity.isCanceled();
    }

    /**
     * O cache offline só vale se a licença for utilizável, tiver sido salva há no máximo 7 dias
     * (e não no futuro, com tolerância de 5 min), for da mesma chave e da mesma URL resolvida.
     */
    public static boolean isCacheUsable(
            CheckLicenceEntity cache,
            String licenceKey,
            String resolvedLicenceUrl,
            Date now
    ) {
        if (!isLicenceUsable(cache, now)) return false;

        Date savedAt = cache.getSavedLocalDatabaseAt();
        if (savedAt == null) return false;
        long age = now.getTime() - savedAt.getTime();
        if (age < -CLOCK_SKEW_TOLERANCE_MILLIS) return false;
        if (age > MAX_CACHE_AGE_MILLIS) return false;

        if (licenceKey == null || licenceKey.isEmpty()) return false;
        if (!Objects.equals(cache.getLicenceEncrypt(), licenceKey)) return false;

        if (resolvedLicenceUrl == null) return false;
        // O cache guarda a URL resolvida. Caches antigos guardavam o licenceInternalKey bruto
        // (base64): continuam valendo se decodificarem para a mesma URL oficial.
        return resolvedLicenceUrl.equals(LicenceConfig.decodeAllowedUrl(cache.getLicenceInternalKey()));
    }

    /**
     * Decide o resultado de uma resposta HTTP do servidor de licenças.
     * Sucesso somente com HTTP 200 e uma licença utilizável.
     */
    public static LicenceResult evaluateResponse(int statusCode, String body, Date now) {
        LicenceResult result = new LicenceResult();
        result.setMethod("licence");
        result.setResult(RESULT_ERROR);

        if (statusCode == 200) {
            CheckLicenceEntity entity = body == null ? null : CheckLicenceEntity.fromJson(body);
            if (entity == null || entity.getExpiredAt() == null) {
                result.setErrorMessage(MESSAGE_INVALID_RESPONSE);
                return result;
            }
            if (!isLicenceUsable(entity, now)) {
                result.setLicenceEntity(entity);
                result.setErrorMessage(MESSAGE_LICENCE_NOT_ACTIVE);
                return result;
            }
            result.setLicenceEntity(entity);
            result.setResult(RESULT_SUCCESS);
            return result;
        }

        result.setErrorMessage(extractErrorMessage(statusCode, body));
        return result;
    }

    /** Mensagem do corpo {"message": "..."} ou "Erro ao validar licença (HTTP <code>)". */
    public static String extractErrorMessage(int statusCode, String body) {
        String fallback = "Erro ao validar licença (HTTP " + statusCode + ")";
        if (body == null || body.trim().isEmpty()) return fallback;
        try {
            JSONObject json = new JSONObject(body);
            if (json.isNull("message")) return fallback;
            String message = json.optString("message", "").trim();
            return message.isEmpty() ? fallback : message;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static LicenceResult error(String message) {
        LicenceResult result = new LicenceResult();
        result.setMethod("licence");
        result.setResult(RESULT_ERROR);
        result.setErrorMessage(message);
        return result;
    }
}
