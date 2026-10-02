package br.com.jylabtech.stone_deep_tech;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Testes da ponte StoneDeepTechPlugin -> implementação nativa pré-compilada
 * (android/libs/stone_deep_tech-native.jar).
 *
 * Rode com: cd example/android && ./gradlew :stone_deep_tech:testDebugUnitTest
 * Os testes da validação de licença ficam em android/native/src/test.
 */
public class StoneDeepTechPluginTest {

  @Test
  public void operationWithoutLicence_isBlockedByNativeImplementation() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();
    String[] operations = {"sendDeeplink", "sendCancel", "sendPrint", "sendReprint"};

    for (String method : operations) {
      MethodChannel.Result result = mock(MethodChannel.Result.class);
      plugin.onMethodCall(new MethodCall(method, new HashMap<String, Object>()), result);

      verify(result).error(eq("LICENCE_NOT_CONFIGURED"), anyString(), any());
      verify(result, never()).success(any());
    }
  }

  @Test
  public void configureLicence_requiresLicenceKey() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();
    Map<String, Object> args = new HashMap<>();
    args.put("licenceKey", "");

    MethodChannel.Result result = mock(MethodChannel.Result.class);
    plugin.onMethodCall(new MethodCall("configureLicence", args), result);

    verify(result).error(eq("INVALID_ARGUMENT"), anyString(), any());
    verify(result, never()).success(any());
  }

  @Test
  public void configureLicence_withKey_succeedsOnce() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();
    Map<String, Object> args = new HashMap<>();
    args.put("licenceKey", "test-key");

    MethodChannel.Result result = mock(MethodChannel.Result.class);
    plugin.onMethodCall(new MethodCall("configureLicence", args), result);

    verify(result).success(true);
    verify(result, never()).error(anyString(), any(), any());
    verify(result, never()).notImplemented();
  }

  @Test
  public void detachWithoutAttach_doesNotCrashAndRecreatesDelegate() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();
    plugin.onDetachedFromEngine(mock(FlutterPlugin.FlutterPluginBinding.class));

    MethodChannel.Result result = mock(MethodChannel.Result.class);
    plugin.onMethodCall(new MethodCall("unknown", null), result);
    verify(result).notImplemented();
  }

  @Test
  public void unknownMethod_isNotImplemented() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();
    MethodChannel.Result result = mock(MethodChannel.Result.class);
    plugin.onMethodCall(new MethodCall("unknown", null), result);

    verify(result).notImplemented();
  }
}
