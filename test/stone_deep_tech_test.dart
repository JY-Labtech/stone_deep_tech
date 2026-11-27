import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:stone_deep_tech/stone_deep_tech.dart';

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
      if (call.method == 'initDeeplink') return true;
      if (operationError != null) throw operationError!;
      return true;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

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

  group('initDeeplink', () {
    test('avisa o nativo (initDeeplink, sem argumentos) para entregar respostas pendentes', () async {
      StoneDeepTech(channel).initDeeplink(handler: _RecordingHandler());
      await pumpEventQueue();

      final init = calls.singleWhere((c) => c.method == 'initDeeplink');
      expect(init.arguments, isNull);
    });

    test('é o único método chamado no nativo ao inicializar', () async {
      StoneDeepTech(channel).initDeeplink(handler: _RecordingHandler());
      await pumpEventQueue();
      expect(calls.map((c) => c.method), ['initDeeplink']);
    });
  });

  group('operações chamam o canal direto', () {
    for (final entry in operations.entries) {
      test('${entry.key}: sucesso retorna true e chama onDeeplinkSent', () async {
        final handler = _RecordingHandler();
        final deeplink = StoneDeepTech(channel).initDeeplink(handler: handler);

        expect(await entry.value(deeplink), isTrue);
        expect(calls.where((c) => c.method == entry.key), hasLength(1));
        expect(handler.sentCount, 1);
        expect(handler.errors, isEmpty);
      });

      test('${entry.key}: erro do nativo retorna false e chama onError (nunca sucesso)', () async {
        final handler = _RecordingHandler();
        final deeplink = StoneDeepTech(channel).initDeeplink(handler: handler);
        operationError = PlatformException(code: 'ERROR', message: 'sem app da Stone');

        expect(await entry.value(deeplink), isFalse);
        expect(handler.sentCount, 0);
        expect(handler.errors.single, allOf(contains('ERROR'), contains('sem app da Stone')));
      });
    }
  });

  test('deeplink antes de initDeeplink lança erro', () {
    expect(() => StoneDeepTech(channel).deeplink, throwsA(isA<String>()));
  });
}
