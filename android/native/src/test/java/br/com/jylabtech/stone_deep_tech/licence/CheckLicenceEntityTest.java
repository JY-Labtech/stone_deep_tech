package br.com.jylabtech.stone_deep_tech.licence;

import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.LICENCE_KEY;
import static br.com.jylabtech.stone_deep_tech.licence.LicenceTestData.date;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CheckLicenceEntityTest {

    @Test
    public void parsesCoreLegacyLicenceResponse() {
        CheckLicenceEntity entity = CheckLicenceEntity.fromJson(LicenceTestData.validJson());

        assertNotNull(entity);
        assertEquals("L1", entity.getId());
        assertEquals("uuid-L1", entity.getLicenceKey());
        assertEquals(LICENCE_KEY, entity.getLicenceEncrypt());
        assertEquals(date("2027-10-01T23:59:59"), entity.getExpiredAt());
        assertEquals(date("2026-01-10T08:30:00"), entity.getCreatedAt());
        assertFalse(entity.isExpired());
        assertFalse(entity.isInactive());
        assertFalse(entity.isCanceled());
        assertEquals("", entity.getSubscriptionId());
        assertEquals("one_time", entity.getCheckoutType());
        assertTrue(LicenceRules.isLicenceUsable(entity, LicenceTestData.NOW));
    }

    @Test
    public void parsesCanceledFlag() {
        CheckLicenceEntity entity = CheckLicenceEntity.fromJson(
                LicenceTestData.coreJson("2027-10-01T23:59:59", false, false, true));
        assertNotNull(entity);
        assertTrue(entity.isCanceled());
    }

    @Test
    public void masterKeyExpiry2099() {
        CheckLicenceEntity entity = CheckLicenceEntity.fromJson(
                LicenceTestData.coreJson("2099-12-31T23:59:59", false, false, false));
        assertNotNull(entity);
        assertEquals(date("2099-12-31T23:59:59"), entity.getExpiredAt());
    }

    @Test
    public void nullOrInvalidExpiredAtIsNull() {
        CheckLicenceEntity nullExpiry = CheckLicenceEntity.fromJson(
                LicenceTestData.coreJson(null, false, false, false));
        assertNotNull(nullExpiry);
        assertNull(nullExpiry.getExpiredAt());

        CheckLicenceEntity badExpiry = CheckLicenceEntity.fromJson(
                LicenceTestData.coreJson("31/12/2099", false, false, false));
        assertNotNull(badExpiry);
        assertNull(badExpiry.getExpiredAt());
    }

    @Test
    public void invalidJsonReturnsNull() {
        assertNull(CheckLicenceEntity.fromJson("not json"));
        assertNull(CheckLicenceEntity.fromJson(""));
    }

    @Test
    public void cacheRoundTripKeepsFields() {
        CheckLicenceEntity entity = CheckLicenceEntity.fromJson(LicenceTestData.validJson());
        entity.setLicenceInternalKey(LicenceTestData.CORE_URL);
        entity.setSavedLocalDatabaseAt(LicenceTestData.NOW);
        entity.setCanceled(true);

        CheckLicenceEntity copy = CheckLicenceEntity.fromJson(entity.toJson());
        assertNotNull(copy);
        assertEquals(entity.getExpiredAt(), copy.getExpiredAt());
        assertEquals(LicenceTestData.NOW, copy.getSavedLocalDatabaseAt());
        assertEquals(LicenceTestData.CORE_URL, copy.getLicenceInternalKey());
        assertEquals(LICENCE_KEY, copy.getLicenceEncrypt());
        assertTrue(copy.isCanceled());
    }
}
