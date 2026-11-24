# Stone Deep Tech

<div align="center">
  <img src="assets/stone deep tech.png" alt="Stone" height="300" />
</div>

</br>

## 🚨 Aviso

> **Este plugin é _não oficial_** e foi desenvolvido de forma independente para facilitar a integração de pagamentos via Deeplink da Stone em dispositivos Android Smart POS.

---

Plugin Flutter para integração com o sistema de Deeplinks da Stone para meios de pagamento, impressão e reimpressão de comprovantes.

## 📋 Descrição

O **Stone Deep Tech** é um plugin Flutter que permite que desenvolvedores integrem seus aplicativos com o sistema de pagamento da Stone através de deeplinks, sem precisar lidar diretamente com código nativo Android. O plugin oferece uma interface simples e intuitiva para realizar operações de pagamento, cancelamento, impressão customizada e reimpressão de comprovantes.

> A implementação nativa Android é distribuída como um binário privado embarcado no plugin. Nenhum passo adicional de build é necessário para consumidores.

## ✨ Funcionalidades

- ✅ **Pagamento**: Envio de deeplinks para processamento de pagamentos
- ✅ **Cancelamento**: Cancelamento de transações através de deeplink
- ✅ **Impressão Customizada**: Impressão de conteúdo personalizado (texto, linhas, imagens)
- ✅ **Reimpressão**: Reimpressão de comprovantes de transações anteriores
- ✅ **Validação de Licença**: Sistema integrado de validação de licença
- ✅ **Callbacks Assíncronos**: Recebimento de respostas de forma assíncrona
- ✅ **Type Safety**: Enums para maior segurança de tipos
- ✅ **Suporte Completo**: DEBIT, CREDIT, VOUCHER, INSTANT_PAYMENT e PIX

## 📦 Requisitos

- Dart SDK: >=3.9.2 (Flutter compatível)
- Android: SDK mínimo 24 (Android 7.0)
- Aplicativo Stone instalado no dispositivo
- Licença do plugin ativa (veja [Licença do plugin](#-licença-do-plugin))

## 🚀 Instalação

Adicione o plugin ao seu `pubspec.yaml`:

```yaml
dependencies:
  stone_deep_tech: ^1.2.0
```

Execute:

```bash
flutter pub get
```

## ⚙️ Configuração Android

### 1. Configurar o AndroidManifest.xml

Adicione o `intent-filter` necessário para receber respostas de deeplink no seu `AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    ...>
    <!-- ... outros intent-filters ... -->

    <!-- Intent filter para receber respostas de deeplink da Stone -->
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

**Importante**: O `launchMode="singleTop"` é necessário para garantir que o app não crie múltiplas instâncias ao receber deeplinks.

### 2. Configurar o Scheme de Retorno

Crie ou atualize o arquivo `res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="return_scheme">seu_app_scheme</string>
</resources>
```

Substitua `seu_app_scheme` pelo scheme único do seu aplicativo (ex: `meuapp`, `stone_deep_tech_example`).

### 3. Configurar a Chave de Licença

A chave de licença é enviada pelo código Dart ao inicializar o plugin:

```dart
StoneDeepTech.I.initDeeplink(
  handler: handler,
  licenceKey: const String.fromEnvironment('STONE_DEEP_TECH_LICENCE_KEY'),
);
```

- `licenceKey` (obrigatório): a chave da sua licença, copiada do portal [portal.jylabtech.com.br](https://portal.jylabtech.com.br).
- `licenceInternalKey` (opcional): o padrão é a constante `licenceCheck` exportada pelo plugin (base64 de `https://licence-server.invalid/pos-payments/licence/check`). Não é preciso informar.

> **A validação acontece sempre no servidor oficial da JY Labtech** (`https://licence-server.invalid/pos-payments/licence/check`). O `licenceInternalKey` só é aceito se apontar para esse servidor (https, host `licence-server.invalid`, caminho `/pos-payments/licence/check...`), em texto puro ou base64. Qualquer outro valor é ignorado e o servidor oficial é usado, inclusive o valor antigo (`***REMOVIDO***...`), outros hosts e URLs sem TLS.

Sugerimos definir a chave com `--dart-define` (ou outro mecanismo seguro) ao executar seu app:

```bash
flutter run --dart-define STONE_DEEP_TECH_LICENCE_KEY="sua_chave_aqui"
```

Alternativamente, armazene a chave em um serviço seguro (ex.: vault, remote config) e só então invoque `initDeeplink`. Para trocar a chave depois da inicialização, use `StoneDeepTech.I.deeplink.updateLicence(licenceKey: ...)`.

### 4. Validação de Licença

O plugin valida a licença no nativo **antes de cada operação** (`sendDeeplink`, `sendCancel`, `sendPrint`, `sendReprint`). O app da Stone só é aberto se a licença for válida e ativa. Se a licença for inválida, vencida, suspensa, cancelada, inativa ou não puder ser validada, a operação é bloqueada com o código `LICENCE_ERROR` (ou `LICENCE_NOT_CONFIGURED` se a chave não foi informada), entregue em `IDeepLinkHandler.onError`, e o método retorna `false`. Nesse caso `onDeeplinkSent` não é chamado.

**Como funciona:**

1. A primeira operação depois de abrir o app sempre consulta o servidor oficial da JY Labtech (em background, com timeout de 15 s, sem travar a interface); depois, a licença é consultada de novo a cada **4 horas** de uso. Entre essas consultas a licença salva localmente (criptografada) é usada sem rede
2. A licença só é aceita se o servidor responder com sucesso e ela estiver dentro da validade (`expiredAt`) e não estiver vencida, inativa nem cancelada
3. **Bloqueio imediato:** se o servidor recusar a licença (cliente bloqueado no portal, licença vencida, cancelada ou suspensa, não encontrada, aparelho desativado ou limite de aparelhos), a licença salva é apagada e as operações passam a falhar com `LICENCE_ERROR` na hora
4. **Cache offline:** a licença salva só é usada quando o servidor não responde (sem internet, timeout, 408/429 ou erro 5xx) e por no máximo **72 horas** desde a última validação online, e nunca além do vencimento. Depois de uma falha de rede o plugin espera 5 minutos antes de tentar o servidor de novo. O cache é descartado se a chave mudar, se o relógio do aparelho estiver adulterado (data de gravação no futuro) ou se a licença estiver vencida
5. Passadas 72 horas sem validação online, o aparelho precisa de internet para revalidar
6. Se a validação falhar, todas as operações são bloqueadas

## 🔑 Licença do plugin

- **Onde adquirir:** crie sua conta ou entre no portal [portal.jylabtech.com.br](https://portal.jylabtech.com.br). Quem já tinha conta no portal antigo entra com o mesmo e-mail e a mesma senha.
- **Produto:** "Licença plugin de pagamento". Uma mesma licença vale para qualquer plugin de pagamento da JY Labtech.
- **Planos:**
  - `debug`: cortesia concedida automaticamente no autocadastro, válida por 365 dias e limitada a 2 aparelhos. Ideal para desenvolvimento e testes.
  - `mensal`: cobrado por dispositivo.
- **Limite por dispositivo:** cada aparelho que valida a licença ocupa uma vaga. O aparelho é identificado principalmente pelo ANDROID_ID. Aparelhos podem ser consultados e desativados no portal; um aparelho desativado fica bloqueado.
- **Chaves existentes continuam valendo:** as chaves emitidas antes da migração para o novo portal não mudaram. Chaves novas no formato `JYL-...` também funcionam.
- **Migração do `licenceInternalKey`:** a partir da 1.2.0 o parâmetro é opcional e pode ser removido. O valor antigo (que começa com `***REMOVIDO***...`) não aponta mais para o servidor antigo: ele é ignorado e a validação vai para o servidor oficial.

### Erros de licença

Quando o servidor recusa a licença, a resposta traz `{"message": "..."}`. A mensagem é registrada no Logcat (tag `StoneDeepTechNative`, "Erro na validação de licença: ...") e a operação retorna `LICENCE_ERROR`.

| HTTP | Significado | O que fazer |
| ---- | ----------- | ----------- |
| 404 | Licença não encontrada | Confira a `licenceKey` copiada do portal |
| 412 | Limite de aparelhos atingido ou aparelho desativado | Libere/reative o aparelho no portal ou contrate mais dispositivos |
| 428 | Licença vencida, suspensa ou cancelada | Renove ou regularize o pagamento no portal |
| 403 | Requisição sem chave | Informe `licenceKey` em `initDeeplink` |
| 400 | Aparelho sem identificador estável | Verifique se o aparelho fornece ANDROID_ID |
| 429 | Muitas consultas em pouco tempo | Aguarde alguns instantes e tente novamente |
| 5xx | Erro interno do servidor | Tente novamente mais tarde |

Se o corpo da resposta não trouxer `message`, o erro é `Erro ao validar licença (HTTP <código>)`. Se trouxer `"reason": "customer_blocked"`, a mensagem é `Cliente bloqueado. Entre em contato com a JY Labtech.`. Recusas (4xx, exceto 408 e 429) apagam a licença salva e bloqueiam na hora. Sem internet, em timeout, 408, 429 ou 5xx, a licença salva nas últimas 72 horas é usada; sem ela, o erro informa a falha de conexão.

## 📖 Como Usar

### 1. Inicializar o Plugin

Primeiro, crie um handler para receber os callbacks:

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';

class MyDeepLinkHandler implements IDeepLinkHandler {
  @override
  Future<void> onDeeplinkResponse(String responseUri) async {
    print('Resposta recebida: $responseUri');
    // Processe a resposta aqui
    // Exemplo: extrair código de resultado, ATK, etc.
  }

  @override
  Future<void> onError(String message) async {
    print('Erro: $message');
    // Trate erros aqui
  }

  @override
  Future<void> onDeeplinkSent() async {
    print('Deeplink enviado com sucesso');
  }
}

// Inicialize o plugin
final handler = MyDeepLinkHandler();
StoneDeepTech.I.initDeeplink(
  handler: handler,
  licenceKey: 'SUA_CHAVE',
);
```

### 2. Enviar Pagamento

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';

final params = DeeplinkParams(
  amount: 10000, // R$ 100,00 em centavos
  editableAmount: false, // Não permite editar valor (padrão)
  transactionType: TransactionType.debit, // DEBIT, CREDIT, VOUCHER, INSTANT_PAYMENT, PIX
  returnScheme: 'seu_app_scheme', // Deve corresponder ao strings.xml
  // Parâmetros opcionais:
  installmentCount: 3, // Número de parcelas (2 a 99)
  installmentType: InstallmentType.merchant, // MERCHANT, ISSUER, ou NONE
  orderId: 12345, // ID do pedido
);

final success = await StoneDeepTech.I.deeplink.sendDeeplink(params);
if (success) {
  print('Deeplink de pagamento enviado!');
} else {
  print('Erro ao enviar deeplink');
}
```

**Nota**: O campo `installmentType` só é enviado quando `transactionType` é `credit`.

### 3. Cancelar Transação

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';

final cancelParams = CancelParams(
  returnScheme: 'seu_app_scheme',
  atk: 'codigo_atk_da_transacao', // Código único da transação gerado pelo autorizador
  amount: 10000, // Valor em centavos (opcional)
  editableAmount: false, // Não permite editar valor (padrão)
);

final success = await StoneDeepTech.I.deeplink.sendCancel(cancelParams);
if (success) {
  print('Deeplink de cancelamento enviado!');
}
```

### 4. Impressão Customizada

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';
import 'dart:convert';

// Criar conteúdo de impressão
final printContent = [
  // Texto customizado
  TextPrintContent(
    content: 'LOJA EXEMPLO',
    align: PrintTextAlign.center,
    size: PrintTextSize.big,
  ),

  // Linha simples
  LinePrintContent(content: '--------------------------------'),

  // Texto alinhado à esquerda
  TextPrintContent(
    content: 'Produto: Item 1',
    align: PrintTextAlign.left,
    size: PrintTextSize.small,
  ),

  // Texto alinhado à direita
  TextPrintContent(
    content: 'R\$ 100,00',
    align: PrintTextAlign.right,
    size: PrintTextSize.medium,
  ),

  // Imagem (Base64)
  ImagePrintContent(
    imagePath: 'iVBORw0KGgoAAAANSUhEUgAA...', // String Base64
  ),
];

final printParams = PrintParams(
  schemeReturn: 'seu_app_scheme',
  printableContent: printContent,
  showFeedbackScreen: true, // Mostrar tela de sucesso/erro
);

final success = await StoneDeepTech.I.deeplink.sendPrint(printParams);
if (success) {
  print('Deeplink de impressão enviado!');
}
```

### 5. Reimpressão de Comprovante

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';

final reprintParams = ReprintParams(
  schemeReturn: 'seu_app_scheme',
  atk: 'codigo_atk_da_transacao', // Código único da transação
  typeCustomer: TypeCustomer.client, // CLIENT ou MERCHANT
  showFeedbackScreen: true, // Mostrar tela de sucesso/erro
);

final success = await StoneDeepTech.I.deeplink.sendReprint(reprintParams);
if (success) {
  print('Deeplink de reimpressão enviado!');
}
```

### 6. Processar Resultados de Impressão

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';

class MyDeepLinkHandler implements IDeepLinkHandler {
  @override
  Future<void> onDeeplinkResponse(String responseUri) async {
    // Verificar se é um resultado de impressão
    final printResult = DeeplinkResponseParser.parsePrintResult(responseUri);

    if (printResult != null) {
      switch (printResult) {
        case PrintResult.success:
          print('Impressão realizada com sucesso!');
          break;
        case PrintResult.printerOutOfPaper:
          print('Impressora sem papel ou com tampa aberta');
          break;
        case PrintResult.printerInitError:
          print('Erro ao inicializar impressora');
          break;
        // ... outros casos
        default:
          print('Erro: ${printResult.description}');
      }
    } else {
      // Processar outros tipos de resposta (pagamento, cancelamento, etc.)
      print('Resposta recebida: $responseUri');
    }
  }

  // ... outros métodos
}
```

## 📚 API Completa

### StoneDeepTech

Classe principal do plugin, singleton.

#### Métodos

- `static StoneDeepTech get I` - Retorna a instância singleton
- `Deeplink initDeeplink({required IDeepLinkHandler handler, required String licenceKey, String licenceInternalKey = licenceCheck})` - Inicializa o plugin com um handler e configura a licença (`licenceInternalKey` é opcional; valores fora do servidor oficial são ignorados)

### DeeplinkParams

Parâmetros para envio de pagamento.

#### Propriedades

| Propriedade        | Tipo               | Obrigatório | Padrão  | Descrição                                    |
| ------------------ | ------------------ | ----------- | ------- | -------------------------------------------- |
| `amount`           | `int?`             | Não         | -       | Valor em centavos (ex: 10000 = R$ 100,00)    |
| `editableAmount`   | `bool`             | Não         | `false` | Permite editar valor no app de pagamento     |
| `transactionType`  | `TransactionType?` | Não         | -       | DEBIT, CREDIT, VOUCHER, INSTANT_PAYMENT, PIX |
| `installmentCount` | `int?`             | Não         | -       | Número de parcelas (2 a 99)                  |
| `installmentType`  | `InstallmentType?` | Não         | `NONE`  | MERCHANT, ISSUER, ou NONE                    |
| `orderId`          | `int?`             | Não         | -       | ID do pedido para rastreamento               |
| `returnScheme`     | `String`           | Sim         | -       | Scheme do app para receber resposta          |

#### Enums

**TransactionType:**

- `debit` - Débito
- `credit` - Crédito
- `voucher` - Voucher
- `instantPayment` - Pagamento instantâneo
- `pix` - PIX

**InstallmentType:**

- `merchant` - Parcelado sem juros
- `issuer` - Parcelado com juros
- `none` - À vista (padrão)

### CancelParams

Parâmetros para cancelamento.

#### Propriedades

| Propriedade      | Tipo     | Obrigatório | Padrão  | Descrição                           |
| ---------------- | -------- | ----------- | ------- | ----------------------------------- |
| `returnScheme`   | `String` | Sim         | -       | Scheme do app para receber resposta |
| `atk`            | `String` | Sim         | -       | Código único da transação           |
| `amount`         | `int?`   | Não         | -       | Valor em centavos                   |
| `editableAmount` | `bool`   | Não         | `false` | Permite editar valor                |

### PrintParams

Parâmetros para impressão customizada.

#### Propriedades

| Propriedade          | Tipo                 | Obrigatório | Padrão | Descrição                           |
| -------------------- | -------------------- | ----------- | ------ | ----------------------------------- |
| `schemeReturn`       | `String`             | Sim         | -      | Scheme do app para receber resposta |
| `printableContent`   | `List<PrintContent>` | Sim         | -      | Lista de conteúdo para impressão    |
| `showFeedbackScreen` | `bool`               | Não         | `true` | Mostrar tela de sucesso/erro        |

#### Tipos de Conteúdo

**TextPrintContent:**

- `content` (String) - Texto a ser impresso
- `align` (PrintTextAlign) - center, right, left
- `size` (PrintTextSize) - big, medium, small

**LinePrintContent:**

- `content` (String) - Texto sem customização

**ImagePrintContent:**

- `imagePath` (String) - Imagem em Base64

### ReprintParams

Parâmetros para reimpressão.

#### Propriedades

| Propriedade          | Tipo           | Obrigatório | Padrão | Descrição                           |
| -------------------- | -------------- | ----------- | ------ | ----------------------------------- |
| `schemeReturn`       | `String`       | Sim         | -      | Scheme do app para receber resposta |
| `atk`                | `String`       | Sim         | -      | Código único da transação           |
| `typeCustomer`       | `TypeCustomer` | Sim         | -      | CLIENT ou MERCHANT                  |
| `showFeedbackScreen` | `bool`         | Não         | `true` | Mostrar tela de sucesso/erro        |

#### Enums

**TypeCustomer:**

- `client` - Comprovante do cliente
- `merchant` - Comprovante do lojista

### PrintResult

Resultados possíveis de impressão/reimpressão.

#### Valores

| Resultado                  | Código                     | Descrição                                |
| -------------------------- | -------------------------- | ---------------------------------------- |
| `success`                  | SUCCESS                    | Impressão realizada com sucesso          |
| `printerOutOfPaper`        | PRINTER_OUT_OF_PAPER       | Impressora sem papel ou com tampa aberta |
| `printerInitError`         | PRINTER_INIT_ERROR         | Erro ao inicializar impressora           |
| `printerLowEnergy`         | PRINTER_LOW_ENERGY         | Máquina com baixa energia                |
| `printerBusy`              | PRINTER_BUSY               | Impressora ocupada                       |
| `printerUnsupportedFormat` | PRINTER_UNSUPPORTED_FORMAT | Formato não suportado                    |
| `printerInvalidData`       | PRINTER_INVALID_DATA       | Limite máximo do buffer ultrapassado     |
| `printerOverheating`       | PRINTER_OVERHEATING        | Superaquecimento da impressora           |
| `printerPaperJam`          | PRINTER_PAPER_JAM          | Papel preso na caixa de bobina           |
| `printerPrintError`        | PRINTER_PRINT_ERROR        | Erro genérico da impressora              |

### IDeepLinkHandler

Interface para receber callbacks do plugin.

#### Métodos

- `Future<void> onDeeplinkResponse(String responseUri)` - Chamado quando uma resposta é recebida
- `Future<void> onError(String message)` - Chamado quando ocorre um erro
- `Future<void> onDeeplinkSent()` - Chamado quando o deeplink é enviado

## 💡 Exemplos Completos

### Exemplo 1: Pagamento Simples

```dart
import 'package:flutter/material.dart';
import 'package:stone_deep_tech/stone_deep_tech.dart';

class PaymentPage extends StatefulWidget {
  @override
  _PaymentPageState createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> implements IDeepLinkHandler {
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    StoneDeepTech.I.initDeeplink(
      handler: this,
      licenceKey: 'SUA_CHAVE',
    );
  }

  Future<void> processPayment() async {
    setState(() => _isLoading = true);

    final params = DeeplinkParams(
      amount: 10000, // R$ 100,00
      editableAmount: false,
      transactionType: TransactionType.debit,
      returnScheme: 'meuapp',
    );

    await StoneDeepTech.I.deeplink.sendDeeplink(params);
  }

  @override
  Future<void> onDeeplinkResponse(String responseUri) async {
    setState(() => _isLoading = false);
    print('Resposta: $responseUri');
    // Processar resposta...
  }

  @override
  Future<void> onError(String message) async {
    setState(() => _isLoading = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Erro: $message')),
    );
  }

  @override
  Future<void> onDeeplinkSent() async {
    print('Deeplink enviado');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Pagamento')),
      body: Center(
        child: ElevatedButton(
          onPressed: _isLoading ? null : processPayment,
          child: _isLoading
            ? CircularProgressIndicator()
            : Text('Pagar R\$ 100,00'),
        ),
      ),
    );
  }
}
```

### Exemplo 2: Impressão com Tratamento de Erros

```dart
import 'package:stone_deep_tech/stone_deep_tech.dart';

class PrintHandler implements IDeepLinkHandler {
  @override
  Future<void> onDeeplinkResponse(String responseUri) async {
    final printResult = DeeplinkResponseParser.parsePrintResult(responseUri);

    if (printResult != null) {
      if (printResult == PrintResult.success) {
        print('✅ Impressão realizada com sucesso!');
      } else {
        print('❌ Erro na impressão: ${printResult.description}');
        // Exibir mensagem de erro ao usuário
      }
    }
  }

  @override
  Future<void> onError(String message) async {
    print('Erro: $message');
    if (message.contains('LICENCE_ERROR')) {
      print('Licença inválida ou não configurada');
    }
  }

  @override
  Future<void> onDeeplinkSent() async {
    print('Enviando impressão...');
  }
}

void printReceipt() {
  final handler = PrintHandler();
  StoneDeepTech.I.initDeeplink(
    handler: handler,
    licenceKey: 'SUA_CHAVE',
  );

  final content = [
    TextPrintContent(
      content: 'COMPROVANTE DE VENDA',
      align: PrintTextAlign.center,
      size: PrintTextSize.big,
    ),
    LinePrintContent(content: '--------------------------------'),
    TextPrintContent(
      content: 'Valor: R\$ 100,00',
      align: PrintTextAlign.left,
      size: PrintTextSize.medium,
    ),
  ];

  final params = PrintParams(
    schemeReturn: 'meuapp',
    printableContent: content,
    showFeedbackScreen: true,
  );

  StoneDeepTech.I.deeplink.sendPrint(params);
}
```

## 🔍 Tratamento de Erros

### Erros de Licença

Os métodos do `Deeplink` não lançam exceção: em caso de falha retornam `false` e entregam a mensagem em `IDeepLinkHandler.onError`.

```dart
@override
Future<void> onError(String message) async {
  if (message.contains('LICENCE_ERROR') ||
      message.contains('LICENCE_NOT_CONFIGURED')) {
    // Licença inválida, vencida, sem vaga para o aparelho ou não configurada
    print('Verifique a licença em https://portal.jylabtech.com.br');
  }
}
```

Veja a tabela de [erros de licença](#erros-de-licença) para os motivos retornados pelo servidor.

### Erros de Validação

O plugin valida automaticamente:

- Licença configurada
- Licença válida no servidor
- Parâmetros obrigatórios presentes

### Erros de Impressão

Use o enum `PrintResult` para identificar erros específicos:

```dart
final printResult = DeeplinkResponseParser.parsePrintResult(responseUri);
if (printResult == PrintResult.printerOutOfPaper) {
  // Informar usuário sobre falta de papel
}
```

## 🐛 Troubleshooting

### Problema: Deeplink não abre o app Stone

**Solução:**

1. Verifique se o aplicativo Stone está instalado
2. Verifique se o `returnScheme` está configurado corretamente
3. Verifique os logs do Android para erros

### Problema: Resposta não é recebida

**Solução:**

1. Verifique se o `intent-filter` está configurado no `AndroidManifest.xml`
2. Verifique se o `launchMode="singleTop"` está configurado
3. Verifique se o `returnScheme` corresponde ao `strings.xml`
4. Verifique se o handler está implementado corretamente
5. A resposta só é reconhecida se o scheme da URI de retorno for o mesmo informado em `returnScheme`/`schemeReturn` (sem diferenciar maiúsculas/minúsculas). Outros deeplinks do app são ignorados pelo plugin e seguem normalmente; use um scheme exclusivo para o retorno da Stone
6. Chame `StoneDeepTech.I.initDeeplink` logo na inicialização do app: se o app for aberto pela resposta da Stone antes disso, a resposta fica guardada e é entregue a `onDeeplinkResponse` assim que o plugin for inicializado

### Problema: Erro de licença

**Solução:**

1. Verifique se as chaves estão sendo fornecidas ao chamar `StoneDeepTech.I.initDeeplink`
2. Confira a `licenceKey` no portal [portal.jylabtech.com.br](https://portal.jylabtech.com.br) e se a licença está ativa e dentro da validade
3. Não é preciso informar `licenceInternalKey`: a validação sempre vai para o servidor oficial da JY Labtech
4. Verifique a conexão com a internet (primeira operação após abrir o app e a cada 4 horas; sem internet, a operação só funciona se houver uma validação bem-sucedida nas últimas 72 horas)
5. Veja no Logcat (tag `StoneDeepTechNative`) a mensagem retornada pelo servidor e consulte a tabela de [erros de licença](#erros-de-licença)
6. Erro 412: o limite de aparelhos foi atingido ou o aparelho foi desativado; ajuste no portal
7. Trocou de chave ou de plano? Quando a chave muda, a licença salva offline é ignorada e revalidada no servidor
8. Licença renovada ou reativada no portal? Reabra o app: a primeira operação revalida no servidor (com o app aberto, a revalidação acontece a cada 4 horas)

### Problema: Parcelamento não funciona

**Solução:**

1. Verifique se `transactionType` é `credit`
2. Verifique se `installmentCount` está entre 2 e 99
3. Lembre-se: `installmentType` só é enviado quando `transactionType` é `credit`

## 📁 Estrutura do Projeto

```
lib/
├── src/
│   ├── deeplink/
│   │   └── deeplink.dart              # Classe principal de deeplink
│   ├── interface/
│   │   ├── handler/
│   │   │   └── ideeplink_handler.dart  # Interface de handler
│   │   └── model/
│   │       ├── deeplink_params.dart   # Parâmetros de pagamento
│   │       ├── cancel_params.dart     # Parâmetros de cancelamento
│   │       ├── print_params.dart      # Parâmetros de impressão
│   │       ├── print_result.dart      # Resultados de impressão
│   │       └── reprint_params.dart    # Parâmetros de reimpressão
│   └── licence/
│       ├── constants/
│       │   └── licence_constants.dart  # Constantes de licença
│       └── licence.dart               # Exportação do módulo
└── stone_deep_tech.dart               # Classe principal do plugin
```

## 📝 Notas Importantes

1. **Validação de Licença**: Todas as operações são bloqueadas se a licença não for válida
2. **Thread Safety**: O plugin é thread-safe e pode ser usado de qualquer thread
3. **Singleton**: O plugin usa o padrão singleton, então sempre use `StoneDeepTech.I`
4. **Callbacks**: Os callbacks são assíncronos e podem ser chamados de qualquer thread
5. **Parcelamento**: `installmentType` só é enviado quando `transactionType` é `credit`
6. **Impressão**: Imagens devem estar em formato Base64
7. **Valores**: Todos os valores monetários são em centavos (inteiros); `amount` e `orderId` aceitam inteiros de 64 bits
8. **Resposta única**: cada chamada do plugin recebe exatamente uma resposta do nativo (`true`/`false`); erros de configuração da licença disparados por `initDeeplink` chegam em `onError`
9. **Resultado de impressão**: `PrintResult.fromUri`/`DeeplinkResponseParser.parsePrintResult` leem primeiro o código dos parâmetros `result`, `status`, `code` ou `print_result` da URI de retorno

## 🧪 Testes

Testes Dart (MethodChannel mockado): todos os métodos públicos (argumentos exatos enviados ao nativo, sucesso, `PlatformException`, `LICENCE_ERROR`), as chamadas nativo → Dart, os modelos, os enums e os parsers:

```bash
flutter test
```

Testes nativos JVM, com JDK 17: validação de licença (regras, URL travada no servidor oficial, parse da resposta, cache offline), gate de licença por operação, URI de cada deeplink da Stone (`DeeplinkRequests`), resposta única (`SafeResult`), entrega das respostas da Stone ao Dart e a ponte do plugin:

```bash
cd example/android
./gradlew --no-daemon -p ../../android/native testReleaseUnitTest
./gradlew --no-daemon :stone_deep_tech:testDebugUnitTest
```

O primeiro comando exige `flutter precache --android` no SDK definido em `android/native/local.properties` (`flutter.sdk`) ou em `FLUTTER_SDK`. Depois de alterar `android/native`, regenere os binários com `make build-native` (ou `sh scripts/build_android_aar.sh`; argumentos extras do Gradle via `GRADLE_ARGS`, ex.: `GRADLE_ARGS="--no-daemon -Dorg.gradle.jvmargs=-Xmx2g"`).

## 🔗 Referências

- [Stone Payment SDK](https://developer.stone.com.br/)
- [Flutter Plugin Development](https://flutter.dev/docs/development/packages-and-plugins/developing-packages)

## 📄 Licença

Veja o arquivo LICENSE para mais detalhes.

## 📞 Suporte

Para suporte, abra uma issue no repositório do projeto.

---

**Desenvolvido com ❤️ para facilitar a integração com Stone Payment**

---

<div align="left">
  <table>
  <tr>
    <td align="center"><a href="https://github.com/JY-Labtech"><img src="https://avatars.githubusercontent.com/u/185066406?s=200&v=4" width="100px;" alt=""/><br /><sub><b>JY Labtech</b></sub></a><br /> <a href="https://github.com/JY-Labtech" title="Autor"></a></td>
  </tr>

</table>

</div>

---
