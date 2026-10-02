package br.com.jylabtech.stone_deep_tech.licence;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Dados de teste fictícios (nenhuma chave real). */
final class LicenceTestData {
    static final String LICENCE_KEY = "test-licence-key-0000";
    static final String CORE_URL = LicenceConfig.LICENCE_CHECK_URL;

    private LicenceTestData() {}

    static Date date(String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
            return sdf.parse(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final Date NOW = date("2026-10-01T12:00:00");

    static Date plusDays(Date base, long days) {
        return new Date(base.getTime() + TimeUnit.DAYS.toMillis(days));
    }

    static Date plusMinutes(Date base, long minutes) {
        return new Date(base.getTime() + TimeUnit.MINUTES.toMillis(minutes));
    }

    /** JSON no formato do LegacyLicence do jylabtech_core. */
    static String coreJson(String expiredAt, boolean isExpired, boolean isInactive, boolean isCanceled) {
        return "{"
                + "\"id\":\"L1\",\"uid\":\"L1\",\"deviceUid\":\"\",\"deviceSerialNumber\":\"\","
                + "\"userUid\":\"user-1\",\"productId\":\"prod-1\",\"productName\":\"Licença plugin de pagamento\","
                + "\"productDescription\":\"\",\"integrationLibId\":\"\",\"integrationLibName\":\"pos_payments\","
                + "\"checkoutSessionId\":\"\",\"licenceKey\":\"uuid-L1\",\"licenceEncrypt\":\"" + LICENCE_KEY + "\","
                + "\"expiredAt\":" + (expiredAt == null ? "null" : "\"" + expiredAt + "\"") + ","
                + "\"isExpired\":" + isExpired + ",\"isInactive\":" + isInactive + ","
                + "\"isTrial\":false,\"isDebug\":true,\"isCanceled\":" + isCanceled + ","
                + "\"canceledAt\":null,\"licencesQuantity\":-1,\"subscriptionId\":\"\",\"subscription\":null,"
                + "\"checkoutType\":\"one_time\",\"createdAt\":\"2026-01-10T08:30:00\","
                + "\"updatedAt\":\"2026-09-01T10:00:00\",\"coreToken\":\"header.payload.signature\""
                + "}";
    }

    static String validJson() {
        return coreJson("2027-10-01T23:59:59", false, false, false);
    }

    static CheckLicenceEntity entity(Date expiredAt) {
        CheckLicenceEntity entity = new CheckLicenceEntity();
        entity.setExpiredAt(expiredAt);
        entity.setLicenceEncrypt(LICENCE_KEY);
        return entity;
    }

    static CheckLicenceEntity validCache() {
        CheckLicenceEntity entity = entity(plusDays(NOW, 30));
        entity.setSavedLocalDatabaseAt(plusDays(NOW, -1));
        entity.setLicenceInternalKey(CORE_URL);
        return entity;
    }
}
