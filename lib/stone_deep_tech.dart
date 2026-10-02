import 'package:flutter/services.dart';
import 'package:stone_deep_tech/src/deeplink/deeplink.dart';
import 'package:stone_deep_tech/src/interface/handler/ideeplink_handler.dart';
import 'package:stone_deep_tech/src/licence/constants/licence_constants.dart';

export 'src/deeplink/deeplink.dart';
export 'src/interface/handler/ideeplink_handler.dart';
export 'src/interface/model/deeplink_params.dart';
export 'src/interface/model/cancel_params.dart';
export 'src/interface/model/print_params.dart';
export 'src/interface/model/print_result.dart';
export 'src/interface/model/reprint_params.dart';
export 'src/licence/licence.dart';

class StoneDeepTech {
  static const channelName = "stone_deep_tech";
  final MethodChannel _channel;
  Deeplink? _deeplink;

  static StoneDeepTech? _instance;

  StoneDeepTech(this._channel);

  static StoneDeepTech get I {
    _instance ??= StoneDeepTech(const MethodChannel(channelName));
    return _instance!;
  }

  /// Function to initialize deeplink and register the handler.
  ///
  /// [licenceInternalKey] é opcional (padrão: [licenceCheck], servidor oficial da
  /// JY Labtech). Valores que não apontam para o servidor oficial são ignorados
  /// pelo nativo, que sempre valida em `portal-api.jylabtech.com.br`.
  Deeplink initDeeplink({
    required IDeepLinkHandler handler,
    required String licenceKey,
    String licenceInternalKey = licenceCheck,
  }) {
    // Deeplink.create reaproveita a instância existente: troca o handler e
    // reconfigura a licença (falhas do nativo vão para handler.onError).
    _deeplink = Deeplink.create(
      channel: _channel,
      handler: handler,
      licenceKey: licenceKey,
      licenceInternalKey: licenceInternalKey,
    );
    return _deeplink!;
  }

  /// Get the Deeplink object. Note: It needs to be initialized.
  Deeplink get deeplink {
    if (_deeplink == null) {
      throw "DEEPLINK NEEDS TO BE INITIALIZED! \n TRY: StoneDeepTech.I.initDeeplink(handler: ..., licenceKey: '...')";
    }
    return _deeplink!;
  }
}
