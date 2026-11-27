# Stone Deep Tech

> Flutter plugin for Stone Smart POS terminals via deeplink: payments, cancellation, custom printing and receipt reprint on Android. Open source (MIT).

[![pub package](https://img.shields.io/pub/v/stone_deep_tech.svg)](https://pub.dev/packages/stone_deep_tech)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<div align="center">
  <img src="assets/stone deep tech.png" alt="Stone Deep Tech" height="300" />
</div>

Plugin Flutter para integrar apps às maquininhas **Stone Smart POS** pelo sistema de **deeplinks** da Stone: pagamento, cancelamento, impressão customizada e reimpressão de comprovantes, sem escrever código nativo.

O plugin não usa o SDK da Stone. Ele abre os apps da Stone já instalados na maquininha (`payment-app://pay`, `cancel-app://cancel`, `printer-app://print`, `reprinter-app://reprint`) e devolve ao seu app a URI de retorno que a Stone envia ao terminar.

> **Plugin não oficial.** Foi desenvolvido de forma independente pela JY Labtech e não tem vínculo com a Stone.

## Sumário

- [Funcionalidades](#funcionalidades)
- [Maquininhas compatíveis e requisitos](#maquininhas-compatíveis-e-requisitos)
- [Instalação](#instalação)
- [Configuração Android](#configuração-android)
- [Uso rápido](#uso-rápido)
- [Referência da API](#referência-da-api)
- [Exemplos por operação](#exemplos-por-operação)
- [Tratamento de erros](#tratamento-de-erros)
- [App de exemplo](#app-de-exemplo)
- [Testes](#testes)
- [Migração da 1.x para a 2.0](#migração-da-1x-para-a-20)
- [Contribuindo](#contribuindo)
- [Licença](#licença)
- [Créditos](#créditos)

## Funcionalidades

- **Pagamento** (`sendDeeplink`): débito, crédito à vista, crédito parcelado (lojista ou emissor), voucher, PIX e pagamento instantâneo, com valor fixo ou editável e `orderId` opcional.
- **Cancelamento** (`sendCancel`): cancela uma transação pelo `atk`, com valor total ou parcial.
- **Impressão customizada** (`sendPrint`): texto com alinhamento e tamanho, linhas simples e imagens em Base64.
- **Reimpressão** (`sendReprint`): segunda via do comprovante do cliente ou do lojista.
- **Retorno da Stone** (`IDeepLinkHandler.onDeeplinkResponse`): a URI de resposta chega ao seu handler, inclusive quando o app é aberto a frio pela resposta (ela é guardada até o `initDeeplink`).
- **Leitura do resultado de impressão** (`PrintResult`, `DeeplinkResponseParser`).
- Os métodos nunca lançam exceção: retornam `true`/`false` e entregam o erro em `onError`.

## Maquininhas compatíveis e requisitos

| Item | Requisito |
| --- | --- |
| Maquininha | Stone Smart POS com os apps da Stone que atendem os deeplinks de pagamento, cancelamento, impressão e reimpressão |
| Android | `minSdk` 24 (Android 7.0) |
| Dart | `>=3.9.2` |
| Flutter | versão com Dart 3.9 ou superior (Flutter 3.35+) |
| App do fabricante | App de pagamento da Stone instalado e ativado no terminal |
| Permissões | Nenhuma permissão extra. O plugin não acessa a internet |
| Repositório Maven | Nenhum. O plugin não depende do SDK nem do PackageCloud da Stone |

Só Android é suportado.

## Instalação

```bash
flutter pub add stone_deep_tech
```

Ou no `pubspec.yaml`:

```yaml
dependencies:
  stone_deep_tech: ^2.0.0
```

## Configuração Android

### 1. Intent filter de retorno

A Stone devolve o resultado abrindo o seu app com um `ACTION_VIEW` no scheme que você informar em `returnScheme`/`schemeReturn`. Declare esse scheme na Activity principal (`android/app/src/main/AndroidManifest.xml`):

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop"
    ...>

    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Retorno dos deeplinks da Stone -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:host="pay-response"
            android:scheme="@string/return_scheme" />
    </intent-filter>
</activity>
```

Use `launchMode="singleTop"`: assim a resposta chega por `onNewIntent` na mesma Activity, sem abrir outra instância do app.

### 2. Scheme de retorno

Em `android/app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="return_scheme">meuapp</string>
</resources>
```

Use um scheme exclusivo para a Stone e passe o mesmo valor em `returnScheme` (pagamento e cancelamento) ou `schemeReturn` (impressão e reimpressão). O plugin só trata como resposta da Stone, e só consome, um `ACTION_VIEW` com um scheme que ele já enviou à Stone (sem diferenciar maiúsculas de minúsculas). Os outros deeplinks do seu app seguem o caminho normal.

Não há flavors, credenciais nem `local.properties` para configurar.

## Uso rápido

```dart
import 'package:flutter/material.dart';
import 'package:stone_deep_tech/stone_deep_tech.dart';

void main() => runApp(const MaterialApp(home: PaymentPage()));

class PaymentPage extends StatefulWidget {
  const PaymentPage({super.key});

  @override
  State<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> implements IDeepLinkHandler {
  String _status = 'Pronto';

  @override
  void initState() {
    super.initState();
    // Registre o handler o quanto antes: respostas que chegarem antes
    // (app aberto a frio pela Stone) são entregues logo depois do registro.
    StoneDeepTech.I.initDeeplink(handler: this);
  }

  Future<void> _pay() async {
    final ok = await StoneDeepTech.I.deeplink.sendDeeplink(
      DeeplinkParams(
        amount: 1000, // R$ 10,00 em centavos
        transactionType: TransactionType.debit,
        returnScheme: 'meuapp', // igual ao @string/return_scheme
      ),
    );
    if (!ok) setState(() => _status = 'Não foi possível abrir a Stone');
  }

  @override
  Future<void> onDeeplinkSent() async => setState(() => _status = 'Aguardando a Stone...');

  @override
  Future<void> onDeeplinkResponse(String responseUri) async {
    // Ex.: meuapp://pay-response?code=0&atk=...
    final uri = Uri.parse(responseUri);
    setState(() => _status = 'Resposta: ${uri.queryParameters}');
  }

  @override
  Future<void> onError(String message) async => setState(() => _status = 'Erro: $message');

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Stone Deep Tech')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_status),
            const SizedBox(height: 16),
            ElevatedButton(onPressed: _pay, child: const Text('Pagar R\$ 10,00 no débito')),
          ],
        ),
      ),
    );
  }
}
```

## Referência da API

### `StoneDeepTech`

Ponto de entrada do plugin.

| Membro | Descrição |
| --- | --- |
| `static StoneDeepTech get I` | Instância singleton (canal `stone_deep_tech`). |
| `static const String channelName` | Nome do MethodChannel: `"stone_deep_tech"`. |
| `Deeplink initDeeplink({required IDeepLinkHandler handler})` | Registra o handler e devolve o `Deeplink`. Pode ser chamado de novo para trocar o handler. Avisa o nativo de que o handler está pronto, e o nativo entrega as respostas da Stone recebidas antes. Uma falha nesse aviso chega em `handler.onError`. |
| `Deeplink get deeplink` | O `Deeplink` inicializado. Lança uma `String` se `initDeeplink` ainda não foi chamado. |

```dart
final deeplink = StoneDeepTech.I.initDeeplink(handler: MeuHandler());
// depois, em qualquer lugar:
await StoneDeepTech.I.deeplink.sendPrint(params);
```

### `Deeplink`

Todas as operações retornam `Future<bool>`: `true` quando o app da Stone foi aberto (e `onDeeplinkSent` foi chamado), `false` quando houve erro (e `onError` foi chamado). Nenhuma delas lança exceção.

| Método | Parâmetro | App aberto |
| --- | --- | --- |
| `Future<bool> sendDeeplink(DeeplinkParams params)` | pagamento | `payment-app://pay` |
| `Future<bool> sendCancel(CancelParams params)` | cancelamento | `cancel-app://cancel` |
| `Future<bool> sendPrint(PrintParams params)` | impressão customizada | `printer-app://print` |
| `Future<bool> sendReprint(ReprintParams params)` | reimpressão | `reprinter-app://reprint` |
| `void setHandler(IDeepLinkHandler newHandler)` | troca o handler | - |
| `static Deeplink create({required MethodChannel channel, required IDeepLinkHandler handler})` | uso avançado/testes: o mesmo que `initDeeplink` com outro canal | - |
| `IDeepLinkHandler handler` | handler atual | - |

O resultado da transação não vem no `bool`: ele chega depois, em `onDeeplinkResponse`, quando a Stone devolve o controle ao app.

### `IDeepLinkHandler`

```dart
abstract class IDeepLinkHandler {
  /// URI de retorno enviada pela Stone (ex.: meuapp://pay-response?code=0&atk=...).
  Future<void> onDeeplinkResponse(String responseUri);

  /// Erro de uma operação ou do canal (texto da exceção, ex.: "PlatformException(ERROR, ...)").
  Future<void> onError(String message);

  /// O app da Stone foi aberto com sucesso.
  Future<void> onDeeplinkSent();
}
```

### `DeeplinkParams` (pagamento)

| Campo | Tipo | Obrigatório | Padrão | Descrição |
| --- | --- | --- | --- | --- |
| `returnScheme` | `String` | Sim | - | Scheme de retorno (o mesmo do `AndroidManifest`) |
| `amount` | `int?` | Não | - | Valor em centavos (`10000` = R$ 100,00). Aceita 64 bits |
| `editableAmount` | `bool` | Não | `false` | Permite editar o valor no app da Stone |
| `transactionType` | `TransactionType?` | Não | - | Tipo da transação; sem ele a Stone pergunta ao operador |
| `installmentCount` | `int?` | Não | - | Número de parcelas (2 a 99) |
| `installmentType` | `InstallmentType?` | Não | `InstallmentType.none` | Tipo de parcelamento. Só é enviado quando `transactionType` é `credit` |
| `orderId` | `int?` | Não | - | Identificador do pedido. Aceita 64 bits (ex.: timestamp) |

### `CancelParams` (cancelamento)

| Campo | Tipo | Obrigatório | Padrão | Descrição |
| --- | --- | --- | --- | --- |
| `returnScheme` | `String` | Sim | - | Scheme de retorno |
| `atk` | `String` | Sim | - | Código único da transação gerado pelo autorizador da Stone |
| `amount` | `int?` | Não | - | Valor a cancelar, em centavos |
| `editableAmount` | `bool` | Não | `false` | Permite editar o valor |

### `PrintParams` (impressão customizada)

| Campo | Tipo | Obrigatório | Padrão | Descrição |
| --- | --- | --- | --- | --- |
| `schemeReturn` | `String` | Sim | - | Scheme de retorno |
| `printableContent` | `List<PrintContent>` | Sim | - | Itens a imprimir, em ordem |
| `showFeedbackScreen` | `bool` | Não | `true` | Mostra a tela de sucesso/erro da Stone no fim |

Itens de `PrintContent`:

| Classe | Campos | Descrição |
| --- | --- | --- |
| `TextPrintContent` | `content`, `align` (`PrintTextAlign`), `size` (`PrintTextSize`) | Texto formatado |
| `LinePrintContent` | `content` | Linha de texto sem formatação |
| `ImagePrintContent` | `imagePath` | Imagem em Base64 |

### `ReprintParams` (reimpressão)

| Campo | Tipo | Obrigatório | Padrão | Descrição |
| --- | --- | --- | --- | --- |
| `schemeReturn` | `String` | Sim | - | Scheme de retorno |
| `atk` | `String` | Sim | - | Código da transação |
| `typeCustomer` | `TypeCustomer` | Sim | - | Via do cliente ou do lojista |
| `showFeedbackScreen` | `bool` | Não | `true` | Mostra a tela de sucesso/erro |

### Enums

| Enum | Valores (`value` enviado à Stone) |
| --- | --- |
| `TransactionType` | `debit` (`DEBIT`), `credit` (`CREDIT`), `voucher` (`VOUCHER`), `instantPayment` (`INSTANT_PAYMENT`), `pix` (`PIX`) |
| `InstallmentType` | `merchant` (`MERCHANT`, parcelado sem juros), `issuer` (`ISSUER`, parcelado com juros), `none` (`NONE`, à vista) |
| `TypeCustomer` | `client` (`CLIENT`), `merchant` (`MERCHANT`) |
| `PrintTextAlign` | `center`, `right`, `left` |
| `PrintTextSize` | `big`, `medium`, `small` |
| `PrintContentType` | `text`, `line`, `image` |

### `PrintResult` e `DeeplinkResponseParser`

Para interpretar o retorno de `sendPrint`/`sendReprint`:

| Membro | Descrição |
| --- | --- |
| `static PrintResult? PrintResult.fromUri(String uri)` | Lê o código dos parâmetros `result`, `status`, `code` ou `print_result`; se não achar, procura um código conhecido em qualquer parte da URI |
| `static PrintResult? PrintResult.fromString(String result)` | Converte o código (ignora maiúsculas e espaços) |
| `String value` / `String description` | Código da Stone e descrição em português |
| `DeeplinkResponseParser.parsePrintResult(uri)` | O mesmo que `PrintResult.fromUri` |
| `DeeplinkResponseParser.isPrintResult(uri)` | `true` se a URI tem um resultado de impressão |
| `DeeplinkResponseParser.extractResultCode(uri)` | O valor bruto de `result`/`status`/`code`/`print_result` |

| `PrintResult` | Código | Significado |
| --- | --- | --- |
| `success` | `SUCCESS` | Impressão realizada |
| `printerOutOfPaper` | `PRINTER_OUT_OF_PAPER` | Sem papel ou tampa da bobina aberta |
| `printerInitError` | `PRINTER_INIT_ERROR` | Erro ao inicializar a impressora |
| `printerLowEnergy` | `PRINTER_LOW_ENERGY` | Bateria baixa |
| `printerBusy` | `PRINTER_BUSY` | Impressora ocupada |
| `printerUnsupportedFormat` | `PRINTER_UNSUPPORTED_FORMAT` | Formato de conteúdo não suportado |
| `printerInvalidData` | `PRINTER_INVALID_DATA` | Limite do buffer ultrapassado |
| `printerOverheating` | `PRINTER_OVERHEATING` | Superaquecimento |
| `printerPaperJam` | `PRINTER_PAPER_JAM` | Papel preso |
| `printerPrintError` | `PRINTER_PRINT_ERROR` | Erro genérico da impressora |

## Exemplos por operação

Nos exemplos, `deeplink` é `StoneDeepTech.I.deeplink`, já inicializado.

### Débito

```dart
await deeplink.sendDeeplink(DeeplinkParams(
  amount: 2500,
  transactionType: TransactionType.debit,
  returnScheme: 'meuapp',
));
```

### Crédito à vista

```dart
await deeplink.sendDeeplink(DeeplinkParams(
  amount: 15000,
  transactionType: TransactionType.credit,
  installmentType: InstallmentType.none,
  returnScheme: 'meuapp',
));
```

### Crédito parcelado

```dart
await deeplink.sendDeeplink(DeeplinkParams(
  amount: 30000,
  transactionType: TransactionType.credit,
  installmentCount: 3,
  installmentType: InstallmentType.merchant, // sem juros; ISSUER = com juros
  orderId: DateTime.now().millisecondsSinceEpoch,
  returnScheme: 'meuapp',
));
```

### PIX

```dart
await deeplink.sendDeeplink(DeeplinkParams(
  amount: 4990,
  transactionType: TransactionType.pix,
  returnScheme: 'meuapp',
));
```

### Voucher

```dart
await deeplink.sendDeeplink(DeeplinkParams(
  amount: 3200,
  transactionType: TransactionType.voucher,
  returnScheme: 'meuapp',
));
```

### Valor digitado na Stone

```dart
await deeplink.sendDeeplink(DeeplinkParams(
  editableAmount: true,
  returnScheme: 'meuapp',
));
```

### Cancelamento / estorno

```dart
await deeplink.sendCancel(CancelParams(
  returnScheme: 'meuapp',
  atk: atkDaTransacao,
  amount: 15000, // opcional: sem ele a Stone usa o valor da transação
));
```

### Impressão customizada

```dart
await deeplink.sendPrint(PrintParams(
  schemeReturn: 'meuapp',
  printableContent: [
    TextPrintContent(content: 'LOJA EXEMPLO', align: PrintTextAlign.center, size: PrintTextSize.big),
    LinePrintContent(content: '--------------------------------'),
    TextPrintContent(content: 'Total: R\$ 150,00', align: PrintTextAlign.right, size: PrintTextSize.medium),
    ImagePrintContent(imagePath: logoBase64),
  ],
  showFeedbackScreen: false,
));
```

### Reimpressão

```dart
await deeplink.sendReprint(ReprintParams(
  schemeReturn: 'meuapp',
  atk: atkDaTransacao,
  typeCustomer: TypeCustomer.merchant,
));
```

### Tratar o retorno

```dart
@override
Future<void> onDeeplinkResponse(String responseUri) async {
  final printResult = DeeplinkResponseParser.parsePrintResult(responseUri);
  if (printResult != null) {
    debugPrint('Impressão: ${printResult.description}');
    return;
  }
  // Pagamento ou cancelamento: os parâmetros vêm da Stone na própria URI.
  final params = Uri.parse(responseUri).queryParameters;
  debugPrint('Retorno da Stone: $params');
}
```

## Tratamento de erros

Os métodos de `Deeplink` não lançam exceção. Em caso de falha retornam `false` e chamam `onError` com o texto da exceção (por exemplo `PlatformException(ERROR, No Activity found to handle Intent ..., null, null)`).

| Código / exceção | Quando acontece | O que fazer |
| --- | --- | --- |
| `ERROR` | O app da Stone não pôde ser aberto (app ausente, `No Activity found`), argumento com tipo errado ou plugin desanexado do engine | Confirme que é uma maquininha Stone com os apps instalados; confira os parâmetros |
| `stone_deep_tech_missing_binary` | A implementação nativa pré-compilada (`android/libs`) não foi encontrada | Reinstale o pacote; se compilou o nativo localmente, rode `scripts/build_android_aar.sh` |
| `MissingPluginException` | Plataforma sem implementação (ex.: iOS, web) ou plugin não registrado | Use em Android; faça um rebuild completo do app |
| `"DEEPLINK NEEDS TO BE INITIALIZED! ..."` (`String` lançada) | `StoneDeepTech.I.deeplink` antes de `initDeeplink` | Chame `initDeeplink` na inicialização do app |

```dart
@override
Future<void> onError(String message) async {
  if (message.contains('No Activity found')) {
    // App da Stone ausente: provavelmente não é uma maquininha Stone.
  }
  debugPrint('Erro Stone: $message');
}
```

Erros da própria transação (cartão recusado, operação cancelada pelo operador etc.) não passam por `onError`: eles vêm na URI de `onDeeplinkResponse`.

### Problemas comuns

- **A resposta não chega:** confira o `intent-filter`, o `launchMode="singleTop"` e se o scheme do `strings.xml` é o mesmo de `returnScheme`/`schemeReturn`. Chame `initDeeplink` logo na inicialização.
- **Outro deeplink do app parou de funcionar:** use para a Stone um scheme que o app não usa em outros deeplinks.
- **Parcelamento ignorado:** `installmentType` só é enviado com `transactionType: TransactionType.credit`.

## App de exemplo

O diretório [`example/`](example/) tem um app com todas as operações (pagamento, cancelamento, impressão e reimpressão) e um log das respostas.

```bash
cd example
flutter run
```

Rode numa maquininha Stone Smart POS. Em um emulador ou celular comum o app abre, mas as operações retornam `ERROR` porque os apps da Stone não existem.

## Testes

Testes Dart (MethodChannel mockado: argumentos de cada método, sucesso, erro, entrega das respostas do nativo, modelos e parsers):

```bash
flutter test
```

Testes nativos JVM (JDK 17). O primeiro comando testa a implementação nativa (`android/native`: montagem de cada deeplink, resposta única, filtro e fila das respostas da Stone) e o segundo, a ponte do plugin:

```bash
cd example/android
FLUTTER_SDK=/caminho/do/flutter ./gradlew --no-daemon -p ../../android/native testDebugUnitTest
./gradlew --no-daemon :stone_deep_tech:testDebugUnitTest
```

O módulo `android/native` compila contra o `flutter.jar` do SDK informado em `FLUTTER_SDK` (ou `flutter.sdk` em `android/native/local.properties`); rode `flutter precache --android` nesse SDK antes. Depois de alterar `android/native`, regenere os binários de `android/libs` com:

```bash
GRADLE_ARGS="--no-daemon -Dorg.gradle.jvmargs=-Xmx2g" sh scripts/build_android_aar.sh
```

## Migração da 1.x para a 2.0

A 2.0 torna o plugin open source (MIT) e remove toda a validação de licença e a cobrança. Não há mais chave, servidor de licença nem consulta à internet.

O que mudou:

- `StoneDeepTech.initDeeplink` e `Deeplink.create`: os parâmetros `licenceKey` e `licenceInternalKey` foram removidos.
- `Deeplink.updateLicence` foi removido.
- A constante `licenceCheck` e as constantes `CHANNEL_NAME` e `USER_REFERENCE` (exportadas pelo módulo de licença) foram removidas. Use `StoneDeepTech.channelName` no lugar de `CHANNEL_NAME`.
- Os erros `LICENCE_ERROR`, `LICENCE_NOT_CONFIGURED` e `INVALID_ARGUMENT` (do `configureLicence`) não existem mais. As operações abrem a Stone direto.
- O `--dart-define` da chave e as entradas `licenceKey`/`licenceInternalKey` do `local.properties` não são mais lidos e podem ser apagados.
- O plugin não declara mais a permissão `INTERNET`.

Antes (1.x):

```dart
StoneDeepTech.I.initDeeplink(
  handler: handler,
  licenceKey: const String.fromEnvironment('STONE_DEEP_TECH_LICENCE_KEY'),
);

await StoneDeepTech.I.deeplink.updateLicence(licenceKey: novaChave);

@override
Future<void> onError(String message) async {
  if (message.contains('LICENCE_ERROR')) { /* ... */ }
}
```

Depois (2.0):

```dart
StoneDeepTech.I.initDeeplink(handler: handler);

@override
Future<void> onError(String message) async {
  debugPrint(message);
}
```

As operações, os modelos e o handler não mudaram.

## Contribuindo

Issues e pull requests são bem-vindos em [github.com/JY-Labtech/stone_deep_tech](https://github.com/JY-Labtech/stone_deep_tech/issues).

1. Abra uma issue descrevendo o problema ou a proposta.
2. Faça um fork, crie um branch e inclua testes (Dart e, se mexer no nativo, JVM).
3. Rode `flutter analyze`, `flutter test` e os testes nativos antes do PR.
4. Se alterar `android/native`, regenere `android/libs` com `scripts/build_android_aar.sh` no mesmo PR.

## Licença

Distribuído sob a licença MIT. Veja o arquivo [LICENSE](LICENSE).

## Créditos

Desenvolvido e mantido pela [JY Labtech](https://jylabtech.com.br).

<a href="https://github.com/JY-Labtech"><img src="https://avatars.githubusercontent.com/u/185066406?s=200&v=4" width="80" alt="JY Labtech" /></a>
