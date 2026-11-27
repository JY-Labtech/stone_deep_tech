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

/// Testes de cada método público contra o MethodChannel mockado:
/// (a) nome e argumentos exatos, (b) sucesso, (c) erro.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel(StoneDeepTech.channelName);
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final calls = <MethodCall>[];

  /// Erro devolvido pelo nativo nas operações (null = sucesso).
  Object? operationError;

  /// Erro devolvido pelo nativo no initDeeplink (null = sucesso).
  Object? initError;

  setUp(() {
    calls.clear();
    operationError = null;
    initError = null;
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == 'initDeeplink') {
        if (initError != null) throw initError!;
        return true;
      }
      if (operationError != null) throw operationError!;
      return true;
    });
  });

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  Future<Deeplink> init(_RecordingHandler handler) async {
    final deeplink = StoneDeepTech(channel).initDeeplink(handler: handler);
    await pumpEventQueue();
    calls.clear();
    return deeplink;
  }

  MethodCall single(String method) => calls.singleWhere((c) => c.method == method);

  /// Simula o nativo chamando o Dart (resposta da Stone).
  Future<void> nativeCalls(String method, Object? arguments) async {
    await messenger.handlePlatformMessage(
      channel.name,
      const StandardMethodCodec().encodeMethodCall(MethodCall(method, arguments)),
      (_) {},
    );
  }

  group('StoneDeepTech', () {
    test('I é singleton e usa o canal stone_deep_tech', () {
      expect(identical(StoneDeepTech.I, StoneDeepTech.I), isTrue);
      expect(StoneDeepTech.channelName, 'stone_deep_tech');
    });

    test('initDeeplink de novo troca o handler e avisa o nativo de novo', () async {
      final first = _RecordingHandler();
      final second = _RecordingHandler();
      final plugin = StoneDeepTech(channel);
      final d1 = plugin.initDeeplink(handler: first);
      final d2 = plugin.initDeeplink(handler: second);
      await pumpEventQueue();

      expect(identical(d1, d2), isTrue);
      expect(identical(plugin.deeplink, d2), isTrue);
      expect(d2.handler, same(second));
      expect(calls.where((c) => c.method == 'initDeeplink'), hasLength(2));
    });

    test('resposta pendente entregue após o initDeeplink chega ao handler novo', () async {
      // O nativo responde ao initDeeplink e reenvia as respostas do cold start.
      final first = _RecordingHandler();
      final second = _RecordingHandler();
      final plugin = StoneDeepTech(channel);
      plugin.initDeeplink(handler: first);
      plugin.initDeeplink(handler: second);
      await pumpEventQueue();
      await nativeCalls('onDeeplinkResponse', 'meuapp://pay?code=0');

      expect(first.responses, isEmpty);
      expect(second.responses, ['meuapp://pay?code=0']);
    });

    test('falha do nativo no initDeeplink vai para onError (sem erro não tratado)', () async {
      initError = PlatformException(code: 'ERROR', message: 'falhou');
      final handler = _RecordingHandler();
      StoneDeepTech(channel).initDeeplink(handler: handler);
      await pumpEventQueue();

      expect(handler.errors.single, contains('falhou'));
    });

    test('MissingPluginException no initDeeplink também vai para onError', () async {
      initError = MissingPluginException('sem nativo');
      final handler = _RecordingHandler();
      StoneDeepTech(channel).initDeeplink(handler: handler);
      await pumpEventQueue();

      expect(handler.errors.single, contains('MissingPluginException'));
    });
  });

  group('Deeplink.setHandler', () {
    test('troca o handler que recebe os callbacks', () async {
      final deeplink = await init(_RecordingHandler());
      final other = _RecordingHandler();
      deeplink.setHandler(other);

      expect(await deeplink.sendCancel(CancelParams(returnScheme: 'app', atk: '1')), isTrue);
      expect(other.sentCount, 1);
    });
  });

  group('sendDeeplink', () {
    test('(a) envia sendDeeplink com centavos, enums e defaults', () async {
      final deeplink = await init(_RecordingHandler());
      await deeplink.sendDeeplink(DeeplinkParams(
        amount: 1599,
        editableAmount: true,
        transactionType: TransactionType.credit,
        installmentCount: 3,
        installmentType: InstallmentType.merchant,
        orderId: 1727000000000,
        returnScheme: 'meuapp',
      ));

      expect(single('sendDeeplink').arguments, {
        'editableAmount': true,
        'returnScheme': 'meuapp',
        'amount': 1599,
        'transactionType': 'CREDIT',
        'installmentCount': 3,
        'installmentType': 'MERCHANT',
        'orderId': 1727000000000,
      });
    });

    test('(a) só os campos obrigatórios/padrão quando nada é informado', () async {
      final deeplink = await init(_RecordingHandler());
      await deeplink.sendDeeplink(DeeplinkParams(returnScheme: 'meuapp'));
      expect(single('sendDeeplink').arguments, {'editableAmount': false, 'returnScheme': 'meuapp'});
    });

    test('(b) sucesso: true e onDeeplinkSent uma vez', () async {
      final handler = _RecordingHandler();
      final deeplink = await init(handler);
      expect(await deeplink.sendDeeplink(DeeplinkParams(returnScheme: 'app', amount: 100)), isTrue);
      expect(handler.sentCount, 1);
      expect(handler.errors, isEmpty);
    });
  });

  group('sendCancel', () {
    test('(a) envia sendCancel com atk, centavos e editableAmount', () async {
      final deeplink = await init(_RecordingHandler());
      await deeplink.sendCancel(CancelParams(returnScheme: 'meuapp', atk: 'ATK1', amount: 500, editableAmount: true));
      expect(single('sendCancel').arguments, {
        'returnScheme': 'meuapp',
        'atk': 'ATK1',
        'editableAmount': true,
        'amount': 500,
      });
    });

    test('(a) sem amount não envia a chave', () async {
      final deeplink = await init(_RecordingHandler());
      await deeplink.sendCancel(CancelParams(returnScheme: 'meuapp', atk: 'ATK1'));
      expect(single('sendCancel').arguments, {'returnScheme': 'meuapp', 'atk': 'ATK1', 'editableAmount': false});
    });
  });

  group('sendPrint', () {
    test('(a) envia sendPrint com o conteúdo em JSON e showFeedbackScreen padrão true', () async {
      final deeplink = await init(_RecordingHandler());
      await deeplink.sendPrint(PrintParams(
        schemeReturn: 'meuapp',
        printableContent: [
          TextPrintContent(content: 'Olá', align: PrintTextAlign.center, size: PrintTextSize.big),
          LinePrintContent(content: '----'),
          ImagePrintContent(imagePath: 'aGVsbG8='),
        ],
      ));
      expect(single('sendPrint').arguments, {
        'schemeReturn': 'meuapp',
        'printableContent': '[{"type":"text","content":"Olá","align":"center","size":"big"},'
            '{"type":"line","content":"----"},{"type":"image","imagePath":"aGVsbG8="}]',
        'showFeedbackScreen': true,
      });
    });
  });

  group('sendReprint', () {
    test('(a) envia sendReprint com typeCustomer e showFeedbackScreen', () async {
      final deeplink = await init(_RecordingHandler());
      await deeplink.sendReprint(ReprintParams(
        schemeReturn: 'meuapp',
        atk: 'ATK2',
        typeCustomer: TypeCustomer.merchant,
        showFeedbackScreen: false,
      ));
      expect(single('sendReprint').arguments, {
        'schemeReturn': 'meuapp',
        'atk': 'ATK2',
        'typeCustomer': 'MERCHANT',
        'showFeedbackScreen': false,
      });
    });
  });

  group('(b)/(c) todas as operações', () {
    final operations = <String, Future<bool> Function(Deeplink)>{
      'sendDeeplink': (d) => d.sendDeeplink(DeeplinkParams(returnScheme: 'app', amount: 1)),
      'sendCancel': (d) => d.sendCancel(CancelParams(returnScheme: 'app', atk: '1')),
      'sendPrint': (d) => d.sendPrint(PrintParams(schemeReturn: 'app', printableContent: [])),
      'sendReprint': (d) => d.sendReprint(
            ReprintParams(schemeReturn: 'app', atk: '1', typeCustomer: TypeCustomer.client),
          ),
    };

    for (final entry in operations.entries) {
      test('${entry.key}: sucesso chama onDeeplinkSent e o canal uma vez', () async {
        final handler = _RecordingHandler();
        final deeplink = await init(handler);
        expect(await entry.value(deeplink), isTrue);
        expect(calls.where((c) => c.method == entry.key), hasLength(1));
        expect(handler.sentCount, 1);
        expect(handler.errors, isEmpty);
      });

      test('${entry.key}: PlatformException genérica retorna false e chama onError', () async {
        final handler = _RecordingHandler();
        final deeplink = await init(handler);
        operationError = PlatformException(code: 'ERROR', message: 'No Activity found to handle Intent');

        expect(await entry.value(deeplink), isFalse);
        expect(handler.sentCount, 0);
        expect(handler.errors.single, contains('No Activity found'));
      });

      test('${entry.key}: MissingPluginException retorna false', () async {
        final handler = _RecordingHandler();
        final deeplink = await init(handler);
        operationError = MissingPluginException();

        expect(await entry.value(deeplink), isFalse);
        expect(handler.errors, hasLength(1));
      });
    }
  });

  group('respostas do nativo para o Dart', () {
    test('onDeeplinkResponse entrega a URI ao handler atual', () async {
      final handler = _RecordingHandler();
      await init(handler);
      await nativeCalls('onDeeplinkResponse', 'meuapp://pay?code=0&atk=123');
      expect(handler.responses, ['meuapp://pay?code=0&atk=123']);
    });

    test('onDeeplinkResponse sem argumento entrega string vazia', () async {
      final handler = _RecordingHandler();
      await init(handler);
      await nativeCalls('onDeeplinkResponse', null);
      expect(handler.responses, ['']);
    });

    test('onDeeplinkError chama onError (com padrão quando nulo)', () async {
      final handler = _RecordingHandler();
      await init(handler);
      await nativeCalls('onDeeplinkError', 'falhou');
      await nativeCalls('onDeeplinkError', null);
      expect(handler.errors, ['falhou', 'Unknown error']);
    });

    test('método desconhecido vindo do nativo é ignorado', () async {
      final handler = _RecordingHandler();
      await init(handler);
      await nativeCalls('outroMetodo', 'x');
      expect(handler.responses, isEmpty);
      expect(handler.errors, isEmpty);
    });
  });
}
