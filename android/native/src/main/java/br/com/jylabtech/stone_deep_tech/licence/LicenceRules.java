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

    /**
     * Tempo máximo que a licença salva offline vale sem nova consulta bem-sucedida ao servidor
     * (contrato de licenças do jylabtech_core: no máximo 72h; igual ao TTL do token assinado).
     */
    public static final long MAX_CACHE_AGE_MILLIS = TimeUnit.HOURS.toMillis(72);
    /** Com cache válido, consulta o servidor de novo depois deste intervalo (em uso). */
    public static final long REVALIDATE_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(4);
    /** Depois de uma falha de rede, espera este tempo antes de tentar o servidor de novo (usa o cache). */
    public static final long RETRY_AFTER_FAILURE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    /** Motivo do core para cliente bloqueado/banido (bloqueio total imediato). */
    public static final String REASON_CUSTOMER_BLOCKED = "customer_blocked";
    public static final String MESSAGE_CUSTOMER_BLOCKED =
            "Cliente bloqueado. Entre em contato com a JY Labtech.";
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
     * Indica se uma resposta HTTP diferente de 200 é uma recusa definitiva do servidor (licença
     * não encontrada, vencida, cancelada, suspensa, cliente bloqueado, aparelho desativado, limite
     * de aparelhos, requisição inválida). 408, 429, 5xx e outros códigos são tratados como falha
     * temporária (servidor fora do ar), que pode usar o cache offline.
     */
    public static boolean isDefinitiveRejection(int statusCode) {
        return statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429;
    }

    /**
     * Indica se é hora de consultar o servidor: sem cache, na primeira validação deste processo
     * (abertura do app) ou quando a última consulta online tem {@link #REVALIDATE_INTERVAL_MILLIS} ou mais.
     */
    public static boolean shouldRevalidate(CheckLicenceEntity cache, boolean checkedOnlineInThisProcess, Date now) {
        if (cache == null || !checkedOnlineInThisProcess || now == null) return true;
        Date savedAt = cache.getSavedLocalDatabaseAt();
        return savedAt == null || now.getTime() - savedAt.getTime() >= REVALIDATE_INTERVAL_MILLIS;
    }

    /**
     * O cache offline só vale se a licença for utilizável, tiver sido salva há no máximo 72h
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
                // Corpo ilegível (ex.: página de erro de proxy) não é recusa da licença; licença
                // lida mas sem expiredAt é.
                result.setRejectedByServer(entity != null);
                return result;
            }
            if (!isLicenceUsable(entity, now)) {
                result.setLicenceEntity(entity);
                result.setErrorMessage(MESSAGE_LICENCE_NOT_ACTIVE);
                result.setRejectedByServer(true);
                return result;
            }
            result.setLicenceEntity(entity);
            result.setResult(RESULT_SUCCESS);
            return result;
        }

        result.setRejectedByServer(isDefinitiveRejection(statusCode));
        result.setErrorMessage(extractErrorMessage(statusCode, body));
        return result;
    }

    /**
     * Mensagem do corpo {"message": "..."} ou "Erro ao validar licença (HTTP <code>)". Com
     * {"reason": "customer_blocked"} devolve {@link #MESSAGE_CUSTOMER_BLOCKED}.
     */
    public static String extractErrorMessage(int statusCode, String body) {
        String fallback = "Erro ao validar licença (HTTP " + statusCode + ")";
        if (body == null || body.trim().isEmpty()) return fallback;
        try {
            JSONObject json = new JSONObject(body);
            if (REASON_CUSTOMER_BLOCKED.equals(json.optString("reason", ""))) {
                return MESSAGE_CUSTOMER_BLOCKED;
            }
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
