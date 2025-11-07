import 'dart:async';

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

  /// Function to initialize deeplink and register the handler
  Deeplink initDeeplink({
    required IDeepLinkHandler handler,
    required String licenceKey,
    required String licenceInternalKey,
  }) {
    if (_deeplink != null) {
      _deeplink!.setHandler(handler);
      unawaited(
        _deeplink!.updateLicence(
          licenceKey: licenceKey,
          licenceInternalKey: licenceInternalKey,
        ),
      );
      return _deeplink!;
    }
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
      throw "DEEPLINK NEEDS TO BE INITIALIZED! \n TRY: StoneDeepTech.I.initDeeplink(handler: ..., licenceKey: '...', licenceInternalKey: '...')";
    }
    return _deeplink!;
  }
}
