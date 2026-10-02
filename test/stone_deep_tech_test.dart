import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stone_deep_tech/stone_deep_tech.dart';

/// Chave fictícia usada só nos testes.
const _testLicenceKey = 'test-licence-key-0000';

class _RecordingHandler implements IDeepLinkHandler {
  final List<String> errors = [];
  final List<String> responses = [];
  int sentCount = 0;

  @override
  Future<void> onDeeplinkResponse(String responseUri) async => responses.add(responseUri);

  @override
  Future<void> onDeeplinkSent() async => sentCount++;

  @override
  Future<void> onError(String message) async => errors.add(message);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel(StoneDeepTech.channelName);
  final calls = <MethodCall>[];

  /// Resposta do nativo para as operações (null = sucesso).
  PlatformException? operationError;

  setUp(() {
    calls.clear();
    operationError = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == 'configureLicence') return true;
      if (operationError != null) throw operationError!;
      return true;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Map<dynamic, dynamic> lastConfigureArgs() {
    final call = calls.lastWhere((c) => c.method == 'configureLicence');
    return call.arguments as Map<dynamic, dynamic>;
  }

  final operations = <String, Future<bool> Function(Deeplink)>{
    'sendDeeplink': (d) => d.sendDeeplink(
          DeeplinkParams(amount: 1000, transactionType: TransactionType.debit, returnScheme: 'app'),
        ),
    'sendCancel': (d) => d.sendCancel(CancelParams(returnScheme: 'app', atk: '123')),
    'sendPrint': (d) => d.sendPrint(PrintParams(schemeReturn: 'app', printableContent: [])),
    'sendReprint': (d) => d.sendReprint(
          ReprintParams(schemeReturn: 'app', atk: '123', typeCustomer: TypeCustomer.client),
        ),
  };

  group('licenceCheck', () {
    test('decodifica para a URL do servidor oficial da JY Labtech', () {
      final url = utf8.decode(base64.decode(licenceCheck));
      expect(url, 'https://licence-server.invalid/pos-payments/licence/check');
      final uri = Uri.parse(url);
      expect(uri.scheme, 'https');
      expect(uri.host, 'licence-server.invalid');
      expect(uri.path, startsWith('/pos-payments/'));
    });
  });

  group('configureLicence', () {
    test('initDeeplink envia licenceKey e licenceInternalKey padrão do core quando omitido', () async {
      StoneDeepTech(channel).initDeeplink(handler: _RecordingHandler(), licenceKey: ' $_testLicenceKey ');
      await pumpEventQueue();

      final args = lastConfigureArgs();
      expect(args['licenceKey'], _testLicenceKey);
      expect(args['licenceInternalKey'], licenceCheck);
    });

    test('initDeeplink envia o licenceInternalKey informado', () async {
      StoneDeepTech(channel).initDeeplink(
        handler: _RecordingHandler(),
        licenceKey: _testLicenceKey,
        licenceInternalKey: 'https://licence-server.invalid/pos-payments/licence/check',
      );
      await pumpEventQueue();

      expect(
        lastConfigureArgs()['licenceInternalKey'],
        'https://licence-server.invalid/pos-payments/licence/check',
      );
    });

    test('licenceInternalKey vazio usa a constante do core', () async {
      StoneDeepTech(channel).initDeeplink(
        handler: _RecordingHandler(),
        licenceKey: _testLicenceKey,
        licenceInternalKey: '   ',
      );
      await pumpEventQueue();
      expect(lastConfigureArgs()['licenceInternalKey'], licenceCheck);
    });

    test('updateLicence envia as chaves (internalKey padrão do core)', () async {
      final deeplink = StoneDeepTech(channel).initDeeplink(
        handler: _RecordingHandler(),
        licenceKey: _testLicenceKey,
      );
      await deeplink.updateLicence(licenceKey: 'other-test-key');

      final args = lastConfigureArgs();
      expect(args['licenceKey'], 'other-test-key');
      expect(args['licenceInternalKey'], licenceCheck);
    });

    test('licenceKey vazio é rejeitado', () {
      expect(
        () => StoneDeepTech(channel).initDeeplink(handler: _RecordingHandler(), licenceKey: '  '),
        throwsArgumentError,
      );
    });
  });

  group('licença inválida', () {
    for (final entry in operations.entries) {
      test('${entry.key}: LICENCE_ERROR retorna false e chama onError (nunca sucesso)', () async {
        final handler = _RecordingHandler();
        final deeplink = StoneDeepTech(channel).initDeeplink(handler: handler, licenceKey: _testLicenceKey);
        operationError = PlatformException(code: 'LICENCE_ERROR', message: 'Licença vencida');

        final ok = await entry.value(deeplink);

        expect(ok, isFalse);
        expect(handler.sentCount, 0);
        expect(handler.errors, hasLength(1));
        expect(handler.errors.single, contains('LICENCE_ERROR'));
        expect(handler.errors.single, contains('Licença vencida'));
        expect(calls.map((c) => c.method), contains(entry.key));
      });
    }

    test('LICENCE_NOT_CONFIGURED também é entregue como erro', () async {
      final handler = _RecordingHandler();
      final deeplink = StoneDeepTech(channel).initDeeplink(handler: handler, licenceKey: _testLicenceKey);
      operationError = PlatformException(code: 'LICENCE_NOT_CONFIGURED', message: 'Chaves não configuradas');

      expect(await operations['sendDeeplink']!(deeplink), isFalse);
      expect(handler.sentCount, 0);
      expect(handler.errors.single, contains('LICENCE_NOT_CONFIGURED'));
    });
  });

  group('licença válida', () {
    for (final entry in operations.entries) {
      test('${entry.key}: sucesso retorna true e chama onDeeplinkSent', () async {
        final handler = _RecordingHandler();
        final deeplink = StoneDeepTech(channel).initDeeplink(handler: handler, licenceKey: _testLicenceKey);

        expect(await entry.value(deeplink), isTrue);
        expect(handler.sentCount, 1);
        expect(handler.errors, isEmpty);
      });
    }
  });

  test('deeplink antes de initDeeplink lança erro', () {
    expect(() => StoneDeepTech(channel).deeplink, throwsA(isA<String>()));
  });
}
