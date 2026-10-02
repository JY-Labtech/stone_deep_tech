package br.com.jylabtech.stone_deep_tech.licence;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

class LicenceInvalidException extends Exception { LicenceInvalidException(String msg) { super(msg); } }
class LicenceUnAuthorizedException extends Exception { LicenceUnAuthorizedException(String msg) { super(msg); } }
class LicenceNotFoundException extends Exception { LicenceNotFoundException(String msg) { super(msg); } }
class LicenceConditionRequiredException extends Exception { LicenceConditionRequiredException(String msg) { super(msg); } }
class LicenceExpiredException extends Exception { LicenceExpiredException(String msg) { super(msg); } }
class LicenceInternalErrorException extends Exception { LicenceInternalErrorException(String msg) { super(msg); } }
class LicenceErrorException extends Exception { LicenceErrorException(String msg) { super(msg); } }

public class LicenceProvider implements LicenceRemote {

    private static final String TAG = "LicenceProvider";
    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int READ_TIMEOUT_MS = 15_000;

    private final Context context;

    public LicenceProvider(Context context) {
        this.context = context;
    }

    /**
     * Consulta a licença no servidor. Bloqueante: chame fora da main thread.
     * Nunca lança exceção; qualquer falha vira um LicenceResult de erro.
     */
    @Override
    public LicenceResult checkLicence(final String urlString, final String licenceKey) {
        HttpURLConnection connection = null;
        try {
            String deviceId = getAndroidId();
            String deviceUid = getSerialNumber();
            String deviceLocalId = getDeviceUUID();
            String deviceName = Build.MODEL;

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            if (licenceKey != null && !licenceKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer licence:" + licenceKey);
            }

            connection.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            body.put("deviceUid", deviceUid);
            body.put("deviceLocalId", deviceLocalId);
            body.put("deviceName", deviceName);

            OutputStream os = connection.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            String responseBody = responseCode == HttpURLConnection.HTTP_OK
                    ? readStream(connection.getInputStream())
                    : readStream(connection.getErrorStream());

            return LicenceRules.evaluateResponse(responseCode, responseBody, new Date());
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Tempo esgotado ao validar licença", e);
            return LicenceRules.error("Tempo esgotado ao validar licença. Verifique a conexão com a internet.");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao validar licença", e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return LicenceRules.error("Erro ao validar licença: " + detail);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) {
        if (stream == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder responseStr = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseStr.append(line);
            }
            return responseStr.toString();
        } catch (Exception e) {
            Log.d(TAG, "Erro ao ler resposta do servidor de licenças", e);
            return null;
        }
    }

    @SuppressLint("HardwareIds")
    private String getAndroidId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private String getSerialNumber() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Build.getSerial();
            } else {
                return Build.SERIAL != null ? Build.SERIAL : "UNKNOWN_SERIAL";
            }
        } catch (SecurityException e) {
            return "UNKNOWN_SERIAL";
        } catch (Exception e) {
            return "UNKNOWN_SERIAL";
        }
    }

    private String getDeviceUUID() {
        return UUID.randomUUID().toString();
    }
}

