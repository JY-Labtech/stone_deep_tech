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

import org.junit.Test;

/**
 * Testes da ponte StoneDeepTechPlugin -> implementação nativa pré-compilada
 * (android/libs/stone_deep_tech-native.jar).
 *
 * Rode com: cd example/android && ./gradlew :stone_deep_tech:testDebugUnitTest
 * Os testes da implementação nativa ficam em android/native/src/test.
 */
public class StoneDeepTechPluginTest {

  @Test
  public void operationWithoutEngine_repliesErrorOnceFromNativeImplementation() {
    // Sem engine anexado não há Context para abrir o app da Stone: erro explícito, nunca sucesso falso.
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();
    String[] operations = {"sendDeeplink", "sendCancel", "sendPrint", "sendReprint"};

    for (String method : operations) {
      MethodChannel.Result result = mock(MethodChannel.Result.class);
      plugin.onMethodCall(new MethodCall(method, new HashMap<String, Object>()), result);

      verify(result).error(eq("ERROR"), anyString(), any());
      verify(result, never()).success(any());
    }
  }

  @Test
  public void initDeeplink_succeedsOnce() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();

    MethodChannel.Result result = mock(MethodChannel.Result.class);
    plugin.onMethodCall(new MethodCall("initDeeplink", null), result);

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
