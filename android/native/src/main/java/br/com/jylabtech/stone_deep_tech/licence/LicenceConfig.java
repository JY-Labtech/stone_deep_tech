package br.com.jylabtech.stone_deep_tech.licence;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Configuração fixa do servidor de licenças da JY Labtech.
 *
 * A validação sempre acontece no portal oficial. O {@code licenceInternalKey} recebido do app
 * só é aceito se apontar para esse servidor; qualquer outro valor (URL antiga, outro host,
 * http sem TLS, base64 inválido etc.) é ignorado e a URL padrão do core é usada.
 *
 * Classe pura (sem dependências do Android) para poder ser testada em JVM.
 */
public final class LicenceConfig {

    public static final String LICENCE_HOST = "portal-api.jylabtech.com.br";
    public static final String LICENCE_PATH_PREFIX = "/pos-payments/licence/check";
    public static final String LICENCE_CHECK_URL = "https://" + LICENCE_HOST + LICENCE_PATH_PREFIX;

    private LicenceConfig() {}

    /**
     * Resolve a URL efetiva de validação a partir do {@code licenceInternalKey}
     * (URL em texto puro ou em base64). Devolve sempre uma URL do servidor oficial.
     */
    public static String resolveLicenceUrl(String licenceInternalKey) {
        String allowed = decodeAllowedUrl(licenceInternalKey);
        return allowed != null ? allowed : LICENCE_CHECK_URL;
    }

    /**
     * Decodifica o valor e devolve a URL somente se ela for do servidor oficial; caso contrário, null.
     */
    public static String decodeAllowedUrl(String licenceInternalKey) {
        if (licenceInternalKey == null) return null;
        String value = licenceInternalKey.trim();
        if (value.isEmpty()) return null;

        String candidate;
        if (value.toLowerCase(Locale.ROOT).startsWith("http")) {
            candidate = value;
        } else {
            byte[] decoded = decodeBase64(value);
            if (decoded == null) return null;
            candidate = new String(decoded, StandardCharsets.UTF_8).trim();
        }

        return isAllowedUrl(candidate) ? candidate : null;
    }

    /** true somente para https://portal-api.jylabtech.com.br/pos-payments/licence/check... */
    public static boolean isAllowedUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == '\\' || c > 0x7E) return false;
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (uri.getRawUserInfo() != null) return false;
            String host = uri.getHost();
            if (host == null || !LICENCE_HOST.equals(host.toLowerCase(Locale.ROOT))) return false;
            if (uri.getPort() != -1 && uri.getPort() != 443) return false;
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(LICENCE_PATH_PREFIX)) return false;
            String rest = path.substring(LICENCE_PATH_PREFIX.length());
            if (!rest.isEmpty() && !rest.startsWith("/")) return false;
            return !path.contains("..") && !path.contains("%");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Decodificador base64 puro (alfabetos padrão e URL-safe, padding opcional, ignora espaços
     * e quebras de linha). Devolve null se o valor não for base64 válido.
     * Usado no lugar de android.util.Base64 (indisponível em JVM) e java.util.Base64 (API 26+).
     */
    static byte[] decodeBase64(String input) {
        if (input == null) return null;
        StringBuilder clean = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
            clean.append(c);
        }
        int len = clean.length();
        while (len > 0 && clean.charAt(len - 1) == '=') len--;
        if (clean.length() - len > 2) return null;
        if (len == 0 || len % 4 == 1) return null;

        byte[] out = new byte[len * 3 / 4];
        int buffer = 0;
        int bits = 0;
        int pos = 0;
        for (int i = 0; i < len; i++) {
            int v = base64Value(clean.charAt(i));
            if (v < 0) return null;
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[pos++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        if (pos != out.length) {
            byte[] trimmed = new byte[pos];
            System.arraycopy(out, 0, trimmed, 0, pos);
            return trimmed;
        }
        return out;
    }

    private static int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+' || c == '-') return 62;
        if (c == '/' || c == '_') return 63;
        return -1;
    }
}
