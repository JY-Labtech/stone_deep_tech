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

public class CheckLicenceTechTest {

    private static final String OLD_CLOUD_RUN_B64 =
            "***REMOVIDO***";

    static final class FakeRemote implements LicenceRemote {
        int calls = 0;
        String lastUrl;
        String lastKey;
        LicenceResult response;

        @Override
        public LicenceResult checkLicence(String urlString, String licenceKey) {
            calls++;
            lastUrl = urlString;
            lastKey = licenceKey;
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

    private FakeRemote remote;
    private FakeStorage storage;
    private CheckLicenceTech tech;

    @Before
    public void setUp() {
        remote = new FakeRemote();
        storage = new FakeStorage();
        tech = new CheckLicenceTech(remote, storage, () -> NOW);
    }

    @Test
    public void validCacheSkipsNetwork() {
        storage.stored = LicenceTestData.validCache();
        LicenceResult result = tech.validateLicence(LICENCE_KEY, CORE_URL);
        assertTrue(result.isSuccess());
        assertEquals(0, remote.calls);
    }

    @Test
    public void onlineSuccessSavesCacheWithResolvedUrlAndNow() {
        remote.response = LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW);
        LicenceResult result = tech.validateLicence(LICENCE_KEY, null);

        assertTrue(result.isSuccess());
        assertEquals(1, remote.calls);
        assertEquals(CORE_URL, remote.lastUrl);
        assertEquals(LICENCE_KEY, remote.lastKey);
        assertEquals(1, storage.saves);
        assertEquals(CORE_URL, storage.stored.getLicenceInternalKey());
        assertEquals(NOW, storage.stored.getSavedLocalDatabaseAt());

        // Segunda chamada usa o cache novo.
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
        stale.setSavedLocalDatabaseAt(plusDays(NOW, -8));
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
        CheckLicenceEntity cache = LicenceTestData.validCache();
        storage.stored = cache;
        assertTrue(tech.validateExistsLicence());
        cache.setInactive(true);
        assertFalse(tech.validateExistsLicence());
    }

    @Test
    public void successReturnsServerEntity() {
        remote.response = LicenceRules.evaluateResponse(200, LicenceTestData.validJson(), NOW);
        LicenceResult result = tech.validateLicence(LICENCE_KEY, CORE_URL);
        assertSame(remote.response, result);
    }
}
