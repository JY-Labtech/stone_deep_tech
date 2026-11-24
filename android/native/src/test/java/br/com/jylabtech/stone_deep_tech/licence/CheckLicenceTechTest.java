package br.com.jylabtech.stone_deep_tech.licence;

import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.CORE_URL;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.LICENCE_KEY;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.NOW;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.plusDays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class CheckLicenceTechTest {

    private static final String OLD_CLOUD_RUN_B64 =
            "***REMOVIDO***";

    static final class FakeRemote implements LicenceRemote {
        int calls = 0;
        String lastUrl;
        String lastKey;
        LicenceResult response;
        RuntimeException error;

        @Override
        public LicenceResult checkLicence(String urlString, String licenceKey) {
            calls++;
            lastUrl = urlString;
            lastKey = licenceKey;
            if (error != null) throw error;
            return response;
        }
    }

    static final class FakeStorage implements LicenceStorage {
        CheckLicenceEntity stored;
        int saves = 0;
        int deletes = 0;

        @Override
        public CheckLicenceEntity load() {
            return stored;
        }

        @Override
        public void save(CheckLicenceEntity entity) {
            saves++;
            stored = CheckLicenceEntity.fromJson(entity.toJson());
        }

        @Override
        public void delete() {
            deletes++;
            stored = null;
        }
    }

    private Date now;
    private FakeRemote remote;
    private FakeStorage storage;
    private CheckLicenceTech tech;

    @Before
    public void setUp() {
        now = NOW;
        remote = new FakeRemote();
        storage = new FakeStorage();
        tech = new CheckLicenceTech(remote, storage, () -> now);
    }

    private void advance(long millis) {
        now = new Date(now.getTime() + millis);
    }

    private LicenceResult validate() {
        return tech.validateLicence(LICENCE_KEY, CORE_URL);
    }

    private LicenceResult validResponse() {
        return LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), now);
    }

    @Test
    public void firstCheckOfProcessGoesOnlineEvenWithValidCache() {
        storage.stored = LicenceTestData.validCache();
        remote.response = validResponse();
        assertTrue(validate().isSuccess());
        assertEquals(1, remote.calls);
        assertEquals(now, storage.stored.getSavedLocalDatabaseAt());
    }

    @Test
    public void validCacheSkipsNetworkAfterOnlineCheckUntilRevalidationInterval() {
        remote.response = validResponse();
        validate();
        assertEquals(1, remote.calls);

        advance(TimeUnit.HOURS.toMillis(1));
        assertTrue(validate().isSuccess());
        assertEquals(1, remote.calls);
        assertTrue(tech.validateExistsLicence());

        advance(LicenceRules.REVALIDATE_INTERVAL_MILLIS);
        remote.response = validResponse();
        assertTrue(validate().isSuccess());
        assertEquals(2, remote.calls);
    }

    @Test
    public void cacheOnlyShortcutRequiresOnlineCheckInThisProcess() {
        storage.stored = LicenceTestData.validCache();
        // app recém-aberto: o atalho sem rede não libera, força a consulta ao servidor
        assertFalse(tech.validateExistsLicence());
        assertNotNull(storage.stored);

        remote.response = validResponse();
        validate();
        assertTrue(tech.validateExistsLicence());

        advance(LicenceRules.REVALIDATE_INTERVAL_MILLIS);
        assertFalse(tech.validateExistsLicence());

        // servidor fora do ar: o atalho libera pelo cache durante a espera entre tentativas
        remote.response = LicenceRules.error("Tempo esgotado ao validar licença.");
        assertTrue(validate().isSuccess());
        assertTrue(tech.validateExistsLicence());

        advance(LicenceRules.RETRY_AFTER_FAILURE_MILLIS);
        assertFalse(tech.validateExistsLicence());
    }

    @Test
    public void customerBlockedClearsCacheAndBlocksImmediately() {
        remote.response = validResponse();
        validate();
        advance(LicenceRules.REVALIDATE_INTERVAL_MILLIS);
        remote.response = LicenceRules.evaluateResponse(428,
                "{\"message\":\"A data de expiração da licença é inválida. [-]\",\"reason\":\"customer_blocked\"}", now);

        LicenceResult result = validate();
        assertFalse(result.isSuccess());
        assertTrue(result.isRejectedByServer());
        assertEquals(LicenceRules.MESSAGE_CUSTOMER_BLOCKED, result.getErrorMessage());
        assertNull(storage.stored);
        // sem cache: a próxima operação também consulta o servidor e continua bloqueada
        assertFalse(validate().isSuccess());
        assertEquals(3, remote.calls);
        assertFalse(tech.validateExistsLicence());
    }

    @Test
    public void everyDefinitiveRefusalClearsValidCache() {
        for (int code : new int[]{404, 412, 428, 403, 400, 401}) {
            setUp();
            storage.stored = LicenceTestData.validCache();
            remote.response = LicenceRules.evaluateResponse(code, "{\"message\":\"recusada\"}", now);
            LicenceResult result = validate();
            assertFalse("HTTP " + code, result.isSuccess());
            assertEquals("recusada", result.getErrorMessage());
            assertNull("HTTP " + code, storage.stored);
        }
    }

    @Test
    public void online200UnusableClearsValidCache() {
        String[] bodies = {
                LicenceTestData.coreJson("2027-10-01T23:59:59", false, false, true),
                LicenceTestData.coreJson("2027-10-01T23:59:59", false, true, false),
                LicenceTestData.coreJson("2027-10-01T23:59:59", true, false, false),
                LicenceTestData.coreJson("2026-09-30T23:59:59", false, false, false),
                LicenceTestData.coreJson(null, false, false, false),
        };
        for (String body : bodies) {
            setUp();
            storage.stored = LicenceTestData.validCache();
            remote.response = LicenceRules.evaluateResponse(200, body, now);
            LicenceResult result = validate();
            assertFalse(body, result.isSuccess());
            assertTrue(body, result.isRejectedByServer());
            assertNull(body, storage.stored);
        }
    }

    @Test
    public void outageUsesCacheUpTo72Hours() {
        remote.response = validResponse();
        validate();
        remote.response = null;
        remote.error = new IllegalStateException("sem internet");

        advance(TimeUnit.HOURS.toMillis(71));
        assertTrue(validate().isSuccess());
        assertNotNull(storage.stored);

        advance(TimeUnit.HOURS.toMillis(1) + 1000);
        LicenceResult result = validate();
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("sem internet"));
        assertNull(storage.stored);
    }

    @Test
    public void cacheOfFourDaysIsNotUsedDuringOutage() {
        CheckLicenceEntity old = LicenceTestData.validCache();
        old.setSavedLocalDatabaseAt(plusDays(NOW, -4));
        storage.stored = old;
        remote.response = LicenceRules.error("Tempo esgotado ao validar licença.");
        assertFalse(validate().isSuccess());
        assertNull(storage.stored);
    }

    @Test
    public void serverErrorsAndTimeoutsUseCache() {
        for (int code : new int[]{500, 502, 503, 504, 408, 429}) {
            setUp();
            storage.stored = LicenceTestData.validCache();
            remote.response = LicenceRules.evaluateResponse(code, "{\"message\":\"fora do ar\"}", now);
            assertTrue("HTTP " + code, validate().isSuccess());
            assertNotNull("HTTP " + code, storage.stored);
        }
        setUp();
        storage.stored = LicenceTestData.validCache();
        remote.response = LicenceRules.error("Tempo esgotado ao validar licença.");
        assertTrue(validate().isSuccess());
        setUp();
        storage.stored = LicenceTestData.validCache();
        remote.response = null;
        assertTrue(validate().isSuccess());
    }

    @Test
    public void unreadable200UsesCache() {
        storage.stored = LicenceTestData.validCache();
        remote.response = LicenceRules.evaluateResponse(200, "<html>captive portal</html>", now);
        assertTrue(validate().isSuccess());
        assertNotNull(storage.stored);
    }

    @Test
    public void afterOutageWaitsBeforeRetryingServer() {
        storage.stored = LicenceTestData.validCache();
        remote.error = new IllegalStateException("sem internet");
        assertTrue(validate().isSuccess());
        assertEquals(1, remote.calls);

        advance(TimeUnit.MINUTES.toMillis(1));
        assertTrue(validate().isSuccess());
        assertEquals(1, remote.calls);

        advance(LicenceRules.RETRY_AFTER_FAILURE_MILLIS);
        remote.error = null;
        remote.response = LicenceRules.evaluateResponse(428, "{\"message\":\"Licença vencida\"}", now);
        LicenceResult result = validate();
        assertFalse(result.isSuccess());
        assertEquals("Licença vencida", result.getErrorMessage());
        assertEquals(2, remote.calls);
        assertNull(storage.stored);
    }

    @Test
    public void outageWithoutCacheBlocksAndKeepsTrying() {
        remote.error = new IllegalStateException("sem internet");
        assertFalse(validate().isSuccess());
        assertFalse(validate().isSuccess());
        assertEquals(2, remote.calls);
    }

    @Test
    public void onlineSuccessSavesCacheWithResolvedUrlAndNow() {
        remote.response = validResponse();
        LicenceResult result = tech.validateLicence(LICENCE_KEY, null);

        assertTrue(result.isSuccess());
        assertEquals(1, remote.calls);
        assertEquals(CORE_URL, remote.lastUrl);
        assertEquals(LICENCE_KEY, remote.lastKey);
        assertEquals(1, storage.saves);
        assertEquals(CORE_URL, storage.stored.getLicenceInternalKey());
        assertEquals(NOW, storage.stored.getSavedLocalDatabaseAt());

        // Segunda chamada (já validada online neste processo) usa o cache novo.
        assertTrue(tech.validateLicence(LICENCE_KEY, CORE_URL).isSuccess());
        assertEquals(1, remote.calls);
    }

    @Test
    public void foreignInternalKeyIsIgnoredAndCoreIsCalled() {
        remote.response = LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW);
        tech.validateLicence(LICENCE_KEY, "https://evil.com/pos-payments/licence/check");
        assertEquals(CORE_URL, remote.lastUrl);

        storage.stored = null;
        tech.validateLicence(LICENCE_KEY, OLD_CLOUD_RUN_B64);
        assertEquals(CORE_URL, remote.lastUrl);
    }

    @Test
    public void staleCacheIsDeletedAndRevalidatedOnline() {
        CheckLicenceEntity stale = LicenceTestData.validCache();
        stale.setSavedLocalDatabaseAt(plusDays(NOW, -4));
        storage.stored = stale;
        remote.response = LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW);

        assertTrue(tech.validateLicence(LICENCE_KEY, CORE_URL).isSuccess());
        assertEquals(1, storage.deletes);
        assertEquals(1, remote.calls);
        assertEquals(NOW, storage.stored.getSavedLocalDatabaseAt());
    }

    @Test
    public void cacheOfAnotherKeyIsNotUsed() {
        storage.stored = LicenceTestData.validCache();
        remote.response = LicenceRules.evaluateResponse(404, "{\"message\":\"Licença não encontrada\"}", NOW);

        LicenceResult result = tech.validateLicence("other-key", CORE_URL);
        assertFalse(result.isSuccess());
        assertEquals("Licença não encontrada", result.getErrorMessage());
        assertNull(storage.stored);
    }

    @Test
    public void expiredLicenceFromServerIsErrorAndNotCached() {
        remote.response = LicenceRules.evaluateResponse(200,
                LicenceTestData.coreJson("2026-09-30T23:59:59", false, false, false), NOW);
        LicenceResult result = tech.validateLicence(LICENCE_KEY, CORE_URL);
        assertFalse(result.isSuccess());
        assertEquals(0, storage.saves);
        assertNull(storage.stored);
    }

    @Test
    public void successResultWithUnusableEntityIsStillRejected() {
        LicenceResult forged = new LicenceResult();
        forged.setResult(LicenceRules.RESULT_SUCCESS);
        forged.setLicenceEntity(LicenceTestData.entity(plusDays(NOW, -1)));
        remote.response = forged;

        assertFalse(tech.validateLicence(LICENCE_KEY, CORE_URL).isSuccess());
        assertEquals(0, storage.saves);
    }

    @Test
    public void networkErrorIsError() {
        remote.response = LicenceRules.error("Tempo esgotado ao validar licença.");
        LicenceResult result = tech.validateLicence(LICENCE_KEY, CORE_URL);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());

        remote.response = null;
        assertFalse(tech.validateLicence(LICENCE_KEY, CORE_URL).isSuccess());
    }

    @Test
    public void emptyKeyIsErrorWithoutNetwork() {
        assertFalse(tech.validateLicence("", CORE_URL).isSuccess());
        assertFalse(tech.validateLicence(null, CORE_URL).isSuccess());
        assertEquals(0, remote.calls);
    }

    @Test
    public void validateExistsLicenceUsesRules() {
        assertFalse(tech.validateExistsLicence());
        remote.response = validResponse();
        validate();
        CheckLicenceEntity cache = storage.stored;
        assertTrue(tech.validateExistsLicence());
        cache.setInactive(true);
        assertFalse(tech.validateExistsLicence());

        cache.setInactive(false);
        cache.setSavedLocalDatabaseAt(plusDays(now, -4));
        assertFalse(tech.validateExistsLicence());
        assertNull(storage.stored);
    }

    @Test
    public void successReturnsServerEntity() {
        remote.response = LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW);
        LicenceResult result = tech.validateLicence(LICENCE_KEY, CORE_URL);
        assertSame(remote.response, result);
    }
}
