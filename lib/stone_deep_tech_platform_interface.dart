import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'stone_deep_tech_method_channel.dart';

abstract class StoneDeepTechPlatform extends PlatformInterface {
  /// Constructs a StoneDeepTechPlatform.
  StoneDeepTechPlatform() : super(token: _token);

  static final Object _token = Object();

  static StoneDeepTechPlatform _instance = MethodChannelStoneDeepTech();

  /// The default instance of [StoneDeepTechPlatform] to use.
  ///
  /// Defaults to [MethodChannelStoneDeepTech].
  static StoneDeepTechPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [StoneDeepTechPlatform] when
  /// they register themselves.
  static set instance(StoneDeepTechPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
