import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'stone_deep_tech_platform_interface.dart';

/// An implementation of [StoneDeepTechPlatform] that uses method channels.
class MethodChannelStoneDeepTech extends StoneDeepTechPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('stone_deep_tech');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
