import 'dart:async';

import 'package:flutter/services.dart';
import 'package:stone_deep_tech/src/interface/handler/ideeplink_handler.dart';
import 'package:stone_deep_tech/src/interface/model/deeplink_params.dart';
import 'package:stone_deep_tech/src/interface/model/cancel_params.dart';
import 'package:stone_deep_tech/src/interface/model/print_params.dart';
import 'package:stone_deep_tech/src/interface/model/reprint_params.dart';
import 'package:stone_deep_tech/src/licence/constants/licence_constants.dart';

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

  /// Factory method to create Deeplink instance.
  ///
  /// [licenceInternalKey] é opcional (padrão: [licenceCheck]).
  static Deeplink create({
    required MethodChannel channel,
    required IDeepLinkHandler handler,
    required String licenceKey,
    String licenceInternalKey = licenceCheck,
  }) {
    final sanitizedLicenceKey = licenceKey.trim();
    final sanitizedLicenceInternalKey = _sanitizeInternalKey(licenceInternalKey);

    if (sanitizedLicenceKey.isEmpty) {
      throw ArgumentError('licenceKey é obrigatório.');
    }

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

    unawaited(
      _instance!._configureLicenceReportingErrors(
        licenceKey: sanitizedLicenceKey,
        licenceInternalKey: sanitizedLicenceInternalKey,
      ),
    );

    return _instance!;
  }

  /// Configuração disparada sem `await` (em [create]): uma falha do nativo
  /// (ex.: MissingPluginException) vai para [IDeepLinkHandler.onError] em vez
  /// de virar erro assíncrono não tratado.
  Future<void> _configureLicenceReportingErrors({
    required String licenceKey,
    required String licenceInternalKey,
  }) async {
    try {
      await _configureLicence(
        licenceKey: licenceKey,
        licenceInternalKey: licenceInternalKey,
      );
    } catch (e) {
      await handler.onError(e.toString());
    }
  }

  Future<void> _configureLicence({
    required String licenceKey,
    required String licenceInternalKey,
  }) async {
    await channel.invokeMethod('configureLicence', {
      'licenceKey': licenceKey,
      'licenceInternalKey': licenceInternalKey,
    });
  }

  static String _sanitizeInternalKey(String licenceInternalKey) {
    final value = licenceInternalKey.trim();
    return value.isEmpty ? licenceCheck : value;
  }

  /// Atualiza as chaves de licença. [licenceInternalKey] é opcional
  /// (padrão: [licenceCheck]).
  Future<void> updateLicence({
    required String licenceKey,
    String licenceInternalKey = licenceCheck,
  }) {
    final sanitizedLicenceKey = licenceKey.trim();
    final sanitizedLicenceInternalKey = _sanitizeInternalKey(licenceInternalKey);

    if (sanitizedLicenceKey.isEmpty) {
      throw ArgumentError('licenceKey é obrigatório.');
    }

    return _configureLicence(
      licenceKey: sanitizedLicenceKey,
      licenceInternalKey: sanitizedLicenceInternalKey,
    );
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
  ///
  /// O nativo valida a licença antes de abrir o app da Stone; se ela for
  /// inválida, responde PlatformException(code: 'LICENCE_ERROR').
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
