import 'dart:async';

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

  /// Cria (ou reaproveita) a instância e registra o [handler].
  ///
  /// Chamar de novo só troca o handler. Em toda chamada o nativo é avisado
  /// (`initDeeplink`) de que o handler está pronto e entrega as respostas da
  /// Stone que chegaram antes dele (cold start); falhas vão para
  /// [IDeepLinkHandler.onError].
  static Deeplink create({
    required MethodChannel channel,
    required IDeepLinkHandler handler,
  }) {
    if (_instance == null) {
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
    } else {
      _instance!.handler = handler;
    }

    unawaited(_instance!._notifyHandlerReady());

    return _instance!;
  }

  /// Disparado sem `await` (em [create]): uma falha do nativo
  /// (ex.: MissingPluginException) vai para [IDeepLinkHandler.onError] em vez
  /// de virar erro assíncrono não tratado.
  Future<void> _notifyHandlerReady() async {
    try {
      await channel.invokeMethod('initDeeplink');
    } catch (e) {
      await handler.onError(e.toString());
    }
  }

  /// Send deeplink to Stone payment app
  Future<bool> sendDeeplink(DeeplinkParams params) =>
      _invokeOperation('sendDeeplink', params.toMap());

  /// Send cancellation deeplink to Stone payment app
  Future<bool> sendCancel(CancelParams params) =>
      _invokeOperation('sendCancel', params.toMap());

  /// Send custom print deeplink to Stone payment app
  Future<bool> sendPrint(PrintParams params) =>
      _invokeOperation('sendPrint', params.toMap());

  /// Send reprint deeplink to Stone payment app
  Future<bool> sendReprint(ReprintParams params) =>
      _invokeOperation('sendReprint', params.toMap());

  /// Executa uma operação no nativo. Nunca lança: em caso de erro chama
  /// [IDeepLinkHandler.onError] e retorna `false`.
  Future<bool> _invokeOperation(String method, Map<String, dynamic> arguments) async {
    try {
      await channel.invokeMethod(method, arguments);
      await handler.onDeeplinkSent();
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
