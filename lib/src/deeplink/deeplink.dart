import 'package:flutter/services.dart';
import 'package:stone_deep_tech/src/interface/handler/ideeplink_handler.dart';
import 'package:stone_deep_tech/src/interface/model/deeplink_params.dart';
import 'package:stone_deep_tech/src/interface/model/cancel_params.dart';
import 'package:stone_deep_tech/src/interface/model/print_params.dart';
import 'package:stone_deep_tech/src/interface/model/reprint_params.dart';

/// Deeplink class to handle Stone payment deeplinks
final class Deeplink {
  final MethodChannel channel;
  IDeepLinkHandler handler;

  // Cache da instância criada
  static Deeplink? _instance;

  Deeplink._({
    required this.channel,
    required this.handler,
  });

  /// Factory method to create Deeplink instance
  static Deeplink create({
    required MethodChannel channel,
    required IDeepLinkHandler handler,
  }) {
    if (_instance != null) {
      return _instance!;
    }

    final deeplink = Deeplink._(
      channel: channel,
      handler: handler,
    );

    _instance = deeplink;

    // Set up method call handler for receiving deeplink responses
    deeplink.channel.setMethodCallHandler((call) async {
      if (call.method == 'onDeeplinkResponse') {
        final responseUri = call.arguments as String? ?? '';
        await deeplink.handler.onDeeplinkResponse(responseUri);
      } else if (call.method == 'onDeeplinkError') {
        final error = call.arguments as String? ?? 'Unknown error';
        await deeplink.handler.onError(error);
      }
    });

    return deeplink;
  }

  /// Send deeplink to Stone payment app
  Future<bool> sendDeeplink(DeeplinkParams params) async {
    try {
      await handler.onDeeplinkSent();
      await channel.invokeMethod('sendDeeplink', params.toMap());
      return true;
    } catch (e) {
      await handler.onError(e.toString());
      return false;
    }
  }

  /// Send cancellation deeplink to Stone payment app
  Future<bool> sendCancel(CancelParams params) async {
    try {
      await handler.onDeeplinkSent();
      await channel.invokeMethod('sendCancel', params.toMap());
      return true;
    } catch (e) {
      await handler.onError(e.toString());
      return false;
    }
  }

  /// Send custom print deeplink to Stone payment app
  Future<bool> sendPrint(PrintParams params) async {
    try {
      await handler.onDeeplinkSent();
      await channel.invokeMethod('sendPrint', params.toMap());
      return true;
    } catch (e) {
      await handler.onError(e.toString());
      return false;
    }
  }

  /// Send reprint deeplink to Stone payment app
  Future<bool> sendReprint(ReprintParams params) async {
    try {
      await handler.onDeeplinkSent();
      await channel.invokeMethod('sendReprint', params.toMap());
      return true;
    } catch (e) {
      await handler.onError(e.toString());
      return false;
    }
  }

  /// Set handler for deeplink callbacks
  void setHandler(IDeepLinkHandler newHandler) {
    if (_instance == null) {
      throw Exception('Deeplink instance not initialized. Call Deeplink.create() first.');
    }
    _instance!.handler = newHandler;
  }
}
