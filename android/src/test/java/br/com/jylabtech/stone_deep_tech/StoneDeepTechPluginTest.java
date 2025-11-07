package br.com.jylabtech.stone_deep_tech;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import org.junit.Test;

/**
 * This demonstrates a simple unit test of the Java portion of this plugin's implementation.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */

public class StoneDeepTechPluginTest {
  @Test
  public void onMethodCall_withoutNativeBinary_returnsBridgeError() {
    StoneDeepTechPlugin plugin = new StoneDeepTechPlugin();

    final MethodCall call = new MethodCall("sendDeeplink", null);
    MethodChannel.Result mockResult = mock(MethodChannel.Result.class);
    plugin.onMethodCall(call, mockResult);

    verify(mockResult).error(
        eq("stone_deep_tech_missing_binary"),
        contains("Implementação nativa não encontrada"),
        any()
    );
  }
}
