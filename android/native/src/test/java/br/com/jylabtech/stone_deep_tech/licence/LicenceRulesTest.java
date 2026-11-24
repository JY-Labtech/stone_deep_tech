package br.com.jylabtech.stone_deep_tech.licence;

import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.CORE_URL;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.LICENCE_KEY;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.NOW;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.date;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.plusDays;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.plusMinutes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class LicenceRulesTest {

    // ---------- isLicenceUsable ----------

    @Test
    public void usableWhenValidAndActive() {
        assertTrue(LicenceRules.isLicenceUsable(LicenceTestData.entity(plusDays(NOW, 10)), NOW));
    }

    @Test
    public void notUsableWhenExpiredByDate() {
        assertFalse(LicenceRules.isLicenceUsable(LicenceTestData.entity(plusDays(NOW, -1)), NOW));
        assertFalse(LicenceRules.isLicenceUsable(LicenceTestData.entity(NOW), NOW));
    }

    @Test
    public void notUsableWhenIsExpired() {
        CheckLicenceEntity entity = LicenceTestData.entity(plusDays(NOW, 10));
        entity.setExpired(true);
        assertFalse(LicenceRules.isLicenceUsable(entity, NOW));
    }

    @Test
    public void notUsableWhenIsInactive() {
        CheckLicenceEntity entity = LicenceTestData.entity(plusDays(NOW, 10));
        entity.setInactive(true);
        assertFalse(LicenceRules.isLicenceUsable(entity, NOW));
    }

    @Test
    public void notUsableWhenIsCanceled() {
        CheckLicenceEntity entity = LicenceTestData.entity(plusDays(NOW, 10));
        entity.setCanceled(true);
        assertFalse(LicenceRules.isLicenceUsable(entity, NOW));
    }

    @Test
    public void notUsableWhenExpiredAtNullOrEntityNull() {
        assertFalse(LicenceRules.isLicenceUsable(LicenceTestData.entity(null), NOW));
        assertFalse(LicenceRules.isLicenceUsable(null, NOW));
    }

    @Test
    public void masterKey2099IsUsable() {
        assertTrue(LicenceRules.isLicenceUsable(LicenceTestData.entity(date("2099-12-31T23:59:59")), NOW));
    }

    // ---------- evaluateResponse (status, body) ----------

    @Test
    public void http200ValidIsSuccess() {
        LicenceResult result = LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW);
        assertEquals(LicenceRules.RESULT_SUCCESS, result.getResult());
        assertTrue(result.isSuccess());
        assertNotNull(result.getLicenceEntity());
    }

    @Test
    public void http200ExpiredIsError() {
        assertError(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2026-09-30T23:59:59", false, false, false), NOW));
        assertError(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2027-10-01T23:59:59", true, false, false), NOW));
    }

    @Test
    public void http200InactiveIsError() {
        assertError(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2027-10-01T23:59:59", false, true, false), NOW));
    }

    @Test
    public void http200CanceledIsError() {
        assertError(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2027-10-01T23:59:59", false, false, true), NOW));
    }

    @Test
    public void http200InvalidJsonOrWithoutExpiredAtIsError() {
        LicenceResult invalid = LicenceRules.evaluateResponse(200, "<html>oops</html>", NOW);
        assertError(invalid);
        assertEquals(LicenceRules.MESSAGE_INVALID_RESPONSE, invalid.getErrorMessage());
        assertError(LicenceRules.evaluateResponse(200, "{}", NOW));
        assertError(LicenceRules.evaluateResponse(200, null, NOW));
        assertError(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson(null, false, false, false), NOW));
    }

    @Test
    public void errorStatusesUseServerMessage() {
        int[] codes = {404, 412, 428, 403, 400, 429, 500};
        for (int code : codes) {
            LicenceResult result = LicenceRules.evaluateResponse(code,
                    "{\"message\":\"mensagem " + code + "\"}", NOW);
            assertError(result);
            assertEquals("mensagem " + code, result.getErrorMessage());
        }
    }

    @Test
    public void emptyOrNonJsonBodyUsesGenericMessage() {
        assertEquals("Erro ao validar licença (HTTP 500)",
                LicenceRules.evaluateResponse(500, null, NOW).getErrorMessage());
        assertEquals("Erro ao validar licença (HTTP 502)",
                LicenceRules.evaluateResponse(502, "", NOW).getErrorMessage());
        assertEquals("Erro ao validar licença (HTTP 503)",
                LicenceRules.evaluateResponse(503, "<html>Bad gateway</html>", NOW).getErrorMessage());
        assertEquals("Erro ao validar licença (HTTP 404)",
                LicenceRules.evaluateResponse(404, "{\"message\":null}", NOW).getErrorMessage());
    }

    @Test
    public void refusalsAreDefinitiveAndOutagesAreNot() {
        for (int code : new int[]{404, 412, 428, 403, 400}) {
            assertTrue("HTTP " + code, LicenceRules.evaluateResponse(code, "{\"message\":\"x\"}", NOW).isRejectedByServer());
        }
        for (int code : new int[]{408, 429, 500, 502, 503}) {
            assertFalse("HTTP " + code, LicenceRules.evaluateResponse(code, "{\"message\":\"x\"}", NOW).isRejectedByServer());
        }
        assertTrue(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2027-10-01T23:59:59", false, false, true), NOW).isRejectedByServer());
        assertTrue(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2026-09-30T23:59:59", false, false, false), NOW).isRejectedByServer());
        assertTrue(LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson(null, false, false, false), NOW).isRejectedByServer());
        assertFalse(LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW).isRejectedByServer());
        // corpo ilegível (proxy/captive portal) não é recusa da licença
        assertFalse(LicenceRules.evaluateResponse(200, "<html>oops</html>", NOW).isRejectedByServer());
        assertFalse(LicenceRules.evaluateResponse(200, null, NOW).isRejectedByServer());
        assertFalse(LicenceRules.error("timeout").isRejectedByServer());
    }

    @Test
    public void customerBlockedReasonHasClearMessage() {
        LicenceResult result = LicenceRules.evaluateResponse(428,
                "{\"message\":\"A data de expiração da licença é inválida. [-]\",\"reason\":\"customer_blocked\"}", NOW);
        assertTrue(result.isRejectedByServer());
        assertEquals("Cliente bloqueado. Entre em contato com a JY Labtech.", result.getErrorMessage());
    }

    @Test
    public void newResultStartsAsError() {
        assertNotEquals(LicenceRules.RESULT_SUCCESS, new LicenceResult().getResult());
    }

    private static void assertError(LicenceResult result) {
        assertEquals(LicenceRules.RESULT_ERROR, result.getResult());
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ---------- isCacheUsable ----------

    @Test
    public void cacheValid() {
        assertTrue(LicenceRules.isCacheUsable(LicenceTestData.validCache(), LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void cacheOlderThan72HoursIsInvalid() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setSavedLocalDatabaseAt(plusMinutes(plusDays(NOW, -3), -1));
        assertFalse(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));

        cache.setSavedLocalDatabaseAt(plusDays(NOW, -3));
        assertTrue(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));

        cache.setSavedLocalDatabaseAt(plusMinutes(plusDays(NOW, -3), 1));
        assertTrue(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void cacheOfFourDaysIsNoLongerValid() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setSavedLocalDatabaseAt(plusDays(NOW, -4));
        assertFalse(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void shouldRevalidateOnFirstCheckOfProcessAndAfterInterval() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setSavedLocalDatabaseAt(plusMinutes(NOW, -10));
        assertTrue(LicenceRules.shouldRevalidate(cache, false, NOW));
        assertFalse(LicenceRules.shouldRevalidate(cache, true, NOW));
        assertTrue(LicenceRules.shouldRevalidate(null, true, NOW));
        cache.setSavedLocalDatabaseAt(new java.util.Date(NOW.getTime() - LicenceRules.REVALIDATE_INTERVAL_MILLIS));
        assertTrue(LicenceRules.shouldRevalidate(cache, true, NOW));
        cache.setSavedLocalDatabaseAt(null);
        assertTrue(LicenceRules.shouldRevalidate(cache, true, NOW));
    }

    @Test
    public void definitiveRejectionCodes() {
        for (int code : new int[]{400, 401, 402, 403, 404, 409, 410, 412, 423, 428}) {
            assertTrue("HTTP " + code, LicenceRules.isDefinitiveRejection(code));
        }
        for (int code : new int[]{200, 301, 408, 429, 500, 502, 503, 504}) {
            assertFalse("HTTP " + code, LicenceRules.isDefinitiveRejection(code));
        }
    }

    @Test
    public void constantsFollowCoreContract() {
        assertEquals(java.util.concurrent.TimeUnit.HOURS.toMillis(72), LicenceRules.MAX_CACHE_AGE_MILLIS);
        assertEquals(java.util.concurrent.TimeUnit.HOURS.toMillis(4), LicenceRules.REVALIDATE_INTERVAL_MILLIS);
        assertEquals(java.util.concurrent.TimeUnit.MINUTES.toMillis(5), LicenceRules.RETRY_AFTER_FAILURE_MILLIS);
    }

    @Test
    public void cacheSavedInTheFutureIsInvalid() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setSavedLocalDatabaseAt(plusMinutes(NOW, 10));
        assertFalse(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));

        cache.setSavedLocalDatabaseAt(plusMinutes(NOW, 4));
        assertTrue(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void cacheWithoutSavedAtIsInvalid() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setSavedLocalDatabaseAt(null);
        assertFalse(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void cacheWithDifferentKeyIsInvalid() {
        assertFalse(LicenceRules.isCacheUsable(LicenceTestData.validCache(), "other-key", CORE_URL, NOW));
        assertFalse(LicenceRules.isCacheUsable(LicenceTestData.validCache(), "", CORE_URL, NOW));
    }

    @Test
    public void cacheWithDifferentUrlIsInvalid() {
        assertFalse(LicenceRules.isCacheUsable(LicenceTestData.validCache(), LICENCE_KEY,
                CORE_URL + "/invoice", NOW));

        CheckLicenceEntity forged = LicenceTestData.validCache();
        forged.setLicenceInternalKey("https://evil.com/pos-payments/licence/check");
        assertFalse(LicenceRules.isCacheUsable(forged, LICENCE_KEY, CORE_URL, NOW));

        CheckLicenceEntity old = LicenceTestData.validCache();
        old.setLicenceInternalKey(
                "https://pos-payments-api-577464235408.southamerica-east1.run.app/pos-payments/licence/check");
        assertFalse(LicenceRules.isCacheUsable(old, LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void legacyCacheWithCoreBase64StillValid() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setLicenceInternalKey(Base64.getEncoder().encodeToString(CORE_URL.getBytes(StandardCharsets.UTF_8)));
        assertTrue(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));
    }

    @Test
    public void cacheWithExpiredLicenceIsInvalid() {
        CheckLicenceEntity cache = LicenceTestData.validCache();
        cache.setExpiredAt(plusDays(NOW, -1));
        assertFalse(LicenceRules.isCacheUsable(cache, LICENCE_KEY, CORE_URL, NOW));

        CheckLicenceEntity canceled = LicenceTestData.validCache();
        canceled.setCanceled(true);
        assertFalse(LicenceRules.isCacheUsable(canceled, LICENCE_KEY, CORE_URL, NOW));
    }
}
