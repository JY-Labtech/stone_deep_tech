import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:stone_deep_tech/stone_deep_tech.dart';

void main() {
  group('enums', () {
    test('TransactionType.value', () {
      expect(TransactionType.values.map((e) => e.value), ['DEBIT', 'CREDIT', 'VOUCHER', 'INSTANT_PAYMENT', 'PIX']);
    });

    test('InstallmentType.value', () {
      expect(InstallmentType.values.map((e) => e.value), ['MERCHANT', 'ISSUER', 'NONE']);
    });

    test('TypeCustomer.value', () {
      expect(TypeCustomer.values.map((e) => e.value), ['CLIENT', 'MERCHANT']);
    });

    test('PrintContentType / PrintTextAlign / PrintTextSize', () {
      expect(PrintContentType.values.map((e) => e.value), ['text', 'line', 'image']);
      expect(PrintTextAlign.values.map((e) => e.value), ['center', 'right', 'left']);
      expect(PrintTextSize.values.map((e) => e.value), ['big', 'medium', 'small']);
    });
  });

  group('DeeplinkParams.toMap', () {
    test('padrões: installmentType NONE não é enviado sem crédito', () {
      final params = DeeplinkParams(returnScheme: 'app');
      expect(params.installmentType, InstallmentType.none);
      expect(params.toMap(), {'editableAmount': false, 'returnScheme': 'app'});
    });

    test('installmentType só vai com CREDIT', () {
      for (final type in TransactionType.values) {
        final map = DeeplinkParams(
          returnScheme: 'app',
          transactionType: type,
          installmentType: InstallmentType.issuer,
        ).toMap();
        expect(map.containsKey('installmentType'), type == TransactionType.credit, reason: type.name);
        expect(map['transactionType'], type.value);
      }
    });

    test('installmentType nulo com CREDIT não é enviado', () {
      final map = DeeplinkParams(
        returnScheme: 'app',
        transactionType: TransactionType.credit,
        installmentType: null,
        installmentCount: 2,
      ).toMap();
      expect(map.containsKey('installmentType'), isFalse);
      expect(map['installmentCount'], 2);
    });

    test('valores em centavos (int) e orderId grande preservados', () {
      final map = DeeplinkParams(returnScheme: 'app', amount: 123456789, orderId: 9007199254740).toMap();
      expect(map['amount'], isA<int>());
      expect(map['amount'], 123456789);
      expect(map['orderId'], 9007199254740);
    });
  });

  group('CancelParams.toMap', () {
    test('com e sem amount', () {
      expect(CancelParams(returnScheme: 'app', atk: 'A').toMap(), {
        'returnScheme': 'app',
        'atk': 'A',
        'editableAmount': false,
      });
      expect(CancelParams(returnScheme: 'app', atk: 'A', amount: 10, editableAmount: true).toMap(), {
        'returnScheme': 'app',
        'atk': 'A',
        'editableAmount': true,
        'amount': 10,
      });
    });
  });

  group('PrintParams / PrintContent', () {
    test('toJson de cada conteúdo', () {
      expect(
        TextPrintContent(content: 'a', align: PrintTextAlign.right, size: PrintTextSize.small).toJson(),
        {'type': 'text', 'content': 'a', 'align': 'right', 'size': 'small'},
      );
      expect(LinePrintContent(content: 'b').toJson(), {'type': 'line', 'content': 'b'});
      expect(ImagePrintContent(imagePath: 'c').toJson(), {'type': 'image', 'imagePath': 'c'});
      expect(LinePrintContent(content: 'b').type, PrintContentType.line);
    });

    test('toMap serializa a lista como JSON válido e mantém a ordem', () {
      final map = PrintParams(
        schemeReturn: 'app',
        showFeedbackScreen: false,
        printableContent: [
          LinePrintContent(content: 'Total: R\$ 10,00 "ok"'),
          TextPrintContent(content: 'ç', align: PrintTextAlign.left, size: PrintTextSize.medium),
        ],
      ).toMap();
      expect(map['schemeReturn'], 'app');
      expect(map['showFeedbackScreen'], isFalse);
      final decoded = jsonDecode(map['printableContent'] as String) as List;
      expect(decoded, hasLength(2));
      expect(decoded.first['content'], 'Total: R\$ 10,00 "ok"');
      expect(decoded.last['content'], 'ç');
    });

    test('lista vazia vira "[]"', () {
      expect(PrintParams(schemeReturn: 'app', printableContent: []).toMap()['printableContent'], '[]');
    });
  });

  group('ReprintParams.toMap', () {
    test('padrão showFeedbackScreen true', () {
      expect(ReprintParams(schemeReturn: 'app', atk: 'A', typeCustomer: TypeCustomer.client).toMap(), {
        'schemeReturn': 'app',
        'atk': 'A',
        'typeCustomer': 'CLIENT',
        'showFeedbackScreen': true,
      });
    });
  });

  group('PrintResult', () {
    test('value e description de todos os códigos', () {
      expect(PrintResult.values.map((e) => e.value), [
        'SUCCESS',
        'PRINTER_OUT_OF_PAPER',
        'PRINTER_INIT_ERROR',
        'PRINTER_LOW_ENERGY',
        'PRINTER_BUSY',
        'PRINTER_UNSUPPORTED_FORMAT',
        'PRINTER_INVALID_DATA',
        'PRINTER_OVERHEATING',
        'PRINTER_PAPER_JAM',
        'PRINTER_PRINT_ERROR',
      ]);
      for (final r in PrintResult.values) {
        expect(r.description, isNotEmpty);
      }
      expect(PrintResult.success.description, 'Impressão realizada com sucesso');
    });

    test('fromString ignora caixa e espaços; desconhecido é null', () {
      expect(PrintResult.fromString('printer_busy'), PrintResult.printerBusy);
      expect(PrintResult.fromString(' SUCCESS '), PrintResult.success);
      expect(PrintResult.fromString('NOPE'), isNull);
      expect(PrintResult.fromString(''), isNull);
    });

    test('fromUri lê o parâmetro antes de procurar na URI inteira', () {
      expect(PrintResult.fromUri('meuapp://print?result=PRINTER_OUT_OF_PAPER'), PrintResult.printerOutOfPaper);
      // Antes: "success" no scheme fazia retornar success mesmo com erro no parâmetro.
      expect(PrintResult.fromUri('successapp://print?result=PRINTER_PAPER_JAM'), PrintResult.printerPaperJam);
      expect(PrintResult.fromUri('meuapp://print?status=success'), PrintResult.success);
      expect(PrintResult.fromUri('meuapp://print?code=printer_busy'), PrintResult.printerBusy);
      expect(PrintResult.fromUri('meuapp://print?print_result=PRINTER_LOW_ENERGY'), PrintResult.printerLowEnergy);
    });

    test('fromUri mantém a busca antiga quando não há parâmetro conhecido', () {
      expect(PrintResult.fromUri('meuapp://print/PRINTER_OVERHEATING'), PrintResult.printerOverheating);
      expect(PrintResult.fromUri('meuapp://print?result=XYZ&msg=printer_init_error'), PrintResult.printerInitError);
      expect(PrintResult.fromUri('meuapp://pay?code=0&atk=123'), isNull);
      expect(PrintResult.fromUri(''), isNull);
    });
  });

  group('DeeplinkResponseParser', () {
    test('parsePrintResult / isPrintResult', () {
      expect(DeeplinkResponseParser.parsePrintResult('a://p?result=SUCCESS'), PrintResult.success);
      expect(DeeplinkResponseParser.isPrintResult('a://p?result=SUCCESS'), isTrue);
      expect(DeeplinkResponseParser.isPrintResult('a://pay?code=0'), isFalse);
    });

    test('extractResultCode na ordem result > status > code > print_result', () {
      expect(DeeplinkResponseParser.extractResultCode('a://p?status=S&result=R'), 'R');
      expect(DeeplinkResponseParser.extractResultCode('a://p?code=C&status=S'), 'S');
      expect(DeeplinkResponseParser.extractResultCode('a://p?print_result=P&code=C'), 'C');
      expect(DeeplinkResponseParser.extractResultCode('a://p?print_result=P'), 'P');
      expect(DeeplinkResponseParser.extractResultCode('a://p'), isNull);
    });

    test('URI inválida não lança', () {
      expect(DeeplinkResponseParser.extractResultCode('http://[::1'), isNull);
      expect(DeeplinkResponseParser.parsePrintResult('http://[::1'), isNull);
    });
  });
}
