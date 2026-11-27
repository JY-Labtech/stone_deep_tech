import 'package:flutter/services.dart';
import 'package:stone_deep_tech/src/deeplink/deeplink.dart';
import 'package:stone_deep_tech/src/interface/handler/ideeplink_handler.dart';

export 'src/deeplink/deeplink.dart';
export 'src/interface/handler/ideeplink_handler.dart';
export 'src/interface/model/deeplink_params.dart';
export 'src/interface/model/cancel_params.dart';
export 'src/interface/model/print_params.dart';
export 'src/interface/model/print_result.dart';
export 'src/interface/model/reprint_params.dart';

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

  /// Inicializa o deeplink e registra o [handler] que recebe as respostas da Stone.
  ///
  /// Pode ser chamado de novo para trocar o handler. Respostas da Stone que
  /// chegaram antes (app aberto a frio pelo deeplink de retorno) são entregues
  /// ao [handler] logo após o registro.
  Deeplink initDeeplink({required IDeepLinkHandler handler}) {
    _deeplink = Deeplink.create(channel: _channel, handler: handler);
    return _deeplink!;
  }

  /// Get the Deeplink object. Note: It needs to be initialized.
  Deeplink get deeplink {
    if (_deeplink == null) {
      throw "DEEPLINK NEEDS TO BE INITIALIZED! \n TRY: StoneDeepTech.I.initDeeplink(handler: ...)";
    }
    return _deeplink!;
  }
}
