package br.com.jylabtech.stone_deep_tech.licence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class LicenceConfigTest {

    private static final String CORE = "https://licence-server.invalid/pos-payments/licence/check";
    private static final String OLD_CLOUD_RUN =
            "https://licence-server.invalid/pos-payments/licence/check";

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void constantPointsToCore() {
        assertEquals(CORE, LicenceConfig.LICENCE_CHECK_URL);
    }

    @Test
    public void acceptsCorePlainText() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(CORE));
    }

    @Test
    public void acceptsCoreBase64() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(b64(CORE)));
        // Mesmo valor da constante licenceCheck do Dart.
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "***REMOVIDO***"));
    }

    @Test
    public void acceptsInvoiceAndSubscriptionVariants() {
        assertEquals(CORE + "/invoice", LicenceConfig.resolveLicenceUrl(CORE + "/invoice"));
        assertEquals(CORE + "/subscription", LicenceConfig.resolveLicenceUrl(b64(CORE + "/subscription")));
    }

    @Test
    public void oldCloudRunUrlFallsBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(OLD_CLOUD_RUN));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(b64(OLD_CLOUD_RUN)));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "***REMOVIDO***"));
    }

    @Test
    public void otherHostFallsBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl("https://evil.com/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(b64("https://evil.com/pos-payments/licence/check")));
    }

    @Test
    public void maliciousSuffixAndPathTricksFallBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://licence-server.invalid.evil.com/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://evil.com/licence-server.invalid/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://evil.com/?u=https://licence-server.invalid/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://licence-server.invalid/pos-payments/licence/check/../../other"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://licence-server.invalid/pos-payments/licence/checkout"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://licence-server.invalid/other/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://licence-server.invalid:8443/pos-payments/licence/check"));
    }

    @Test
    public void userInfoFallsBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://licence-server.invalid@evil.com/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "https://user:pass@licence-server.invalid/pos-payments/licence/check"));
    }

    @Test
    public void plainHttpFallsBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                "http://licence-server.invalid/pos-payments/licence/check"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(
                b64("http://licence-server.invalid/pos-payments/licence/check")));
    }

    @Test
    public void emptyOrNullFallsBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(null));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(""));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl("   "));
    }

    @Test
    public void garbageBase64FallsBackToCore() {
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl("!!!not-base64!!!"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl("abc"));
        assertEquals(CORE, LicenceConfig.resolveLicenceUrl(b64("not a url at all")));
    }

    @Test
    public void decodeAllowedUrlReturnsNullForRejectedValues() {
        assertNull(LicenceConfig.decodeAllowedUrl(OLD_CLOUD_RUN));
        assertNull(LicenceConfig.decodeAllowedUrl(null));
        assertEquals(CORE, LicenceConfig.decodeAllowedUrl(b64(CORE)));
    }

    @Test
    public void isAllowedUrl() {
        assertTrue(LicenceConfig.isAllowedUrl(CORE));
        assertFalse(LicenceConfig.isAllowedUrl("https://licence-server.invalid/pos-payments/licence/check?x=1"));
        assertFalse(LicenceConfig.isAllowedUrl("https://licence-server.invalid/pos-payments/licence/check#x"));
        assertFalse(LicenceConfig.isAllowedUrl("https://licence-server.invalid\\@evil.com/pos-payments/licence/check"));
    }
}
