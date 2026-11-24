# Análise técnica: stone_deep_tech (1.2.0)

Documento interno, fora do pacote publicado (`doc/` está no `.pubignore`).

## Arquitetura

```
App Flutter
  └─ StoneDeepTech.I.initDeeplink(handler, licenceKey)       lib/stone_deep_tech.dart
       └─ Deeplink (singleton estático)                       lib/src/deeplink/deeplink.dart
            ├─ MethodChannel "stone_deep_tech"  Dart → nativo: configureLicence, sendDeeplink, sendCancel, sendPrint, sendReprint
            └─ MethodChannel "stone_deep_tech"  nativo → Dart: onDeeplinkResponse, onDeeplinkError
Android
  └─ StoneDeepTechPlugin (ponte, android/src)  → reflexão → StoneDeepTechNativePlugin (android/libs, pré-compilado)
       ├─ SafeResult              resposta única por chamada
       ├─ CheckLicenceTech        servidor (LicenceProvider) na 1ª operação e a cada 4h; cache offline (EncryptedLicenceStorage, até 72h) só se o servidor não responder
       ├─ DeeplinkRequests        MethodCall → DeeplinkRequest (lógica pura)
       ├─ StoneGateway            Intent ACTION_VIEW para o app da Stone (IntentStoneGateway)
       └─ DeeplinkResponseDispatcher   intent de retorno da Stone → Dart (com fila até o Dart estar pronto)
```

Não há EventChannel. O resultado da Stone volta como um intent `ACTION_VIEW` com o scheme do app
(`onNewIntent` ou intent de abertura). O nativo repassa a URI ao Dart por `onDeeplinkResponse`, e o app
interpreta a URI (por exemplo com `PrintResult.fromUri`). O plugin não usa SDK da Stone: a "integração"
consiste em abrir os deeplinks `payment-app://pay`, `cancel-app://cancel`, `printer-app://print` e `reprinter-app://reprint`.

## Tabela por método

| Método Dart | Método do canal | Handler nativo | Gate de licença? | Resposta (sucesso / erro) | Testado? | Problemas encontrados |
|---|---|---|---|---|---|---|
| `StoneDeepTech.initDeeplink` / `Deeplink.create` | `configureLicence` `{licenceKey, licenceInternalKey}` | `handleConfigureLicence` | Não (configura) | `true` / `INVALID_ARGUMENT` (licenceKey vazio ou não-texto) | Dart: args, padrão do core, trim, reinit, erro do nativo. JUnit: sucesso, trim, vazio, não-texto, URL padrão, entrega de respostas pendentes | **Corrigido:** chamado com `unawaited`, então um erro do nativo (`INVALID_ARGUMENT`, `MissingPluginException`) virava erro assíncrono não tratado. Agora vai para `handler.onError`. **Corrigido:** `call.argument<String>` com valor não-texto lançava `ClassCastException`. |
| `Deeplink.updateLicence` | `configureLicence` | `handleConfigureLicence` | Não | `Future<void>` completa / lança `PlatformException` ou `ArgumentError` | Dart: args aparados, erro propagado, chave vazia sem chamar o nativo | Nenhum (o erro é propagado de propósito, porque quem chama faz `await`). |
| `Deeplink.sendDeeplink(DeeplinkParams)` | `sendDeeplink` `{editableAmount, returnScheme, amount?, transactionType?, installmentCount?, installmentType? (só CREDIT), orderId?}` | `executeOperation` → `DeeplinkRequests.payment` → `StoneGateway.open` (`payment-app://pay`) | Sim | `true` + `onDeeplinkSent` / `false` + `onError` (`LICENCE_ERROR`, `LICENCE_NOT_CONFIGURED`, `ERROR`) | Dart (a)(b)(c). JUnit: URI completa, Long, tipo errado, gate, falha do gateway | **Corrigido:** `call.argument<Int>` com `amount`/`orderId`/`installmentCount` acima de 2^31 (o Dart envia como Long, por exemplo um `orderId` com timestamp) lançava `ClassCastException`. O pagamento falhava com `ERROR`. |
| `Deeplink.sendCancel(CancelParams)` | `sendCancel` `{returnScheme, atk, editableAmount, amount?}` | `DeeplinkRequests.cancel` (`cancel-app://cancel`) | Sim | idem | Dart (a)(b)(c). JUnit: URI, padrões, tipo errado, gate | Mesmo problema de `Int`/`Long` em `amount` (corrigido). |
| `Deeplink.sendPrint(PrintParams)` | `sendPrint` `{schemeReturn, printableContent (JSON string), showFeedbackScreen}` | `DeeplinkRequests.print` (`printer-app://print`) | Sim | idem | Dart (a)(b)(c), JSON do conteúdo. JUnit: URI, padrões, tipo errado, gate | Nenhum. |
| `Deeplink.sendReprint(ReprintParams)` | `sendReprint` `{schemeReturn, atk, typeCustomer, showFeedbackScreen}` | `DeeplinkRequests.reprint` (`reprinter-app://reprint`) | Sim | idem | Dart (a)(b)(c). JUnit: URI, padrões, gate | Nenhum. |
| `Deeplink.setHandler` | (nenhum) | (nenhum) | Não | troca o handler | Dart | Nenhum. |
| `StoneDeepTech.deeplink` | (nenhum) | (nenhum) | Não | instância / lança `String` se não iniciado | Dart | Lança `String`, não `Exception`. Mantido para não quebrar quem faz `catch`. |
| `IDeepLinkHandler.onDeeplinkResponse` | nativo → Dart `onDeeplinkResponse` (String) | `newIntentListener` / intent de abertura → `DeeplinkResponseDispatcher` | Não | (callback) | Dart: entrega, argumento nulo, método desconhecido. JUnit: envio, fila antes do handler, flush no configureLicence, fila limitada | **Corrigido:** app aberto a frio pela resposta da Stone, com o intent processado antes de `initDeeplink`: a resposta era perdida (o Dart ainda não tinha handler). Agora fica pendente e é entregue no `configureLicence`. **Corrigido:** após rotação (`onReattachedToActivityForConfigChanges`) o intent de abertura era reenviado e o app recebia a resposta duplicada. **Corrigido:** listener de `onNewIntent` nunca era removido. **Corrigido:** qualquer `ACTION_VIEW` era tratado como resposta da Stone e consumido, o que quebrava os deeplinks do app. Agora só é tratado o intent cujo scheme é um dos schemes de retorno enviados à Stone (`return_scheme`, `returnscheme`, `SCHEME_RETURN`); os demais são ignorados e não consumidos. **Corrigido:** com a Activity recriada sem mudança de configuração, a resposta era reentregue. Agora o intent recebe o extra `RESPONSE_HANDLED` e não é entregue de novo. JUnit: filtro, dedup, schemes das 4 operações. |
| `IDeepLinkHandler.onError` (via canal) | nativo → Dart `onDeeplinkError` (String) | (nenhum emissor atual) | Não | (callback) | Dart | O nativo só emitia esse evento se o próprio `invokeMethod` lançasse exceção, e aí o envio também falharia. A falha agora é logada e a resposta fica pendente. O handler Dart foi mantido por compatibilidade. |
| `PrintResult.fromUri` / `fromString` / `DeeplinkResponseParser.*` | (nenhum) | (nenhum) | Não | enum ou null | Dart: todos os códigos, parâmetros, fallback, URI inválida | **Corrigido:** `fromUri` procurava o código em qualquer parte da URI, com `success` primeiro. Um scheme ou host contendo "success" mascarava o erro do parâmetro `result`. Agora o parâmetro tem prioridade e a busca antiga fica como fallback. `fromString` passa a ignorar espaços. |
| Modelos `*Params.toMap`, `PrintContent.toJson`, enums | (nenhum) | (nenhum) | Não | Map / JSON | Dart: payloads realistas, nulos, padrões, valores dos enums | Valores monetários já são `int` em centavos (ok). `installmentCount` é enviado mesmo sem CREDIT, como antes (decisão do dono, ver pendências). |
| (método desconhecido) | qualquer outro | `onMethodCall` | Não | `notImplemented` | JUnit (nativo e ponte) | Nenhum. |

## Bugs encontrados e corrigidos

1. **`ClassCastException` com inteiros grandes** (nativo): `amount`, `orderId` e `installmentCount` eram lidos como `Int`. Acima de 2^31 o codec entrega `Long` e a operação falhava com `ERROR`. Agora os três são lidos como `Long`. O texto na URI é o mesmo (`toLong().toString()`).
2. **Erro assíncrono não tratado no `configureLicence`** (Dart): `Deeplink.create` e `initDeeplink` disparavam a configuração sem `await` e sem `catch`. Agora a falha chama `handler.onError`.
3. **Resposta da Stone perdida no cold start** (nativo): se o intent de retorno chegasse antes do `initDeeplink`, ninguém o recebia. Agora fica numa fila pendente (máximo de 10) e é entregue quando o Dart chama `configureLicence`, logo depois de registrar o handler.
4. **Resposta duplicada após rotação ou recriação da Activity** (nativo): `onReattachedToActivityForConfigChanges` reprocessava o intent de abertura. Agora só `onAttachedToActivity` processa esse intent. Todo intent entregue recebe o extra `br.com.jylabtech.stone_deep_tech.RESPONSE_HANDLED`, então uma Activity recriada com o mesmo intent (por exemplo com "Não manter atividades") não reentrega a resposta. A deduplicação é pela identidade do intent, não pela URI: duas impressões seguidas com `result=SUCCESS` continuam sendo duas respostas.
4b. **`ACTION_VIEW` alheio consumido** (nativo): todo `ACTION_VIEW` era repassado ao Dart como resposta da Stone, e o listener devolvia `true`, o que quebrava os deeplinks do próprio app. Agora o scheme de retorno de cada operação (`return_scheme` no pagamento, `returnscheme` no cancelamento, `SCHEME_RETURN` na impressão e na reimpressão) é registrado antes de abrir a Stone, em minúsculas. Só um intent com esse scheme é tratado e consumido; os demais seguem para o app. O registro fica em SharedPreferences próprio (`stone_deep_tech_return`, só nomes de scheme), para reconhecer a resposta mesmo se o processo morrer com a Stone aberta. Os prefs de licença não mudaram.
5. **Listener de `onNewIntent` não removido** (nativo): agora é removido em `onDetachedFromActivity*`.
6. **Sucesso falso após detach** (nativo): com o engine desanexado, `context?.startActivity` virava no-op e mesmo assim o nativo respondia `success(true)`. Agora o `IntentStoneGateway` lança exceção e a resposta é `ERROR`.
7. **Executor nunca encerrado** (nativo): a thread de validação ficava viva depois de `onDetachedFromEngine`. Agora é encerrada no detach e recriada sob demanda.
8. **Possível Future sem resposta** (nativo): se o executor rejeitasse a tarefa (`RejectedExecutionException`), o `result` nunca era respondido. Agora a resposta é `LICENCE_ERROR`.
9. **`configureLicence` com tipo errado** (nativo): `ClassCastException` passou a ser `INVALID_ARGUMENT`.
10. **`PrintResult.fromUri` frágil** (Dart): ver a tabela acima.

## Otimizações (todas com testes cobrindo antes da mudança)

| Otimização | Por quê |
|---|---|
| `SafeResult` em todas as chamadas | Garante uma única resposta: a segunda é ignorada e logada, em vez de derrubar o app com "Reply already submitted". |
| `EncryptedLicenceStorage` reaproveita o `SharedPreferences` criptografado | Criar `MasterKey` + `EncryptedSharedPreferences` (Keystore + Tink) é caro e acontecia em cada `load/save/delete`, até 3 vezes por validação. Agora a criação acontece uma vez por instância. Se falhar, tenta de novo na próxima chamada. Nome de prefs, chave e formato do JSON não mudaram. |
| `Handler` da main thread criado uma vez | Antes era criado um `Handler(Looper.getMainLooper())` por operação. |
| Executor de licença sob demanda e encerrado no detach | Evita thread vazada por engine. |
| `DeeplinkRequests` (lógica pura) + `StoneGateway` (fachada) | Remove a quadruplicação de `Uri.Builder`/`Intent`/`startActivity`/`try-catch` e permite testar as URIs na JVM sem Robolectric. A ordem e os nomes dos parâmetros na URI são os mesmos de antes. |
| `executeOperation` único no nativo e `_invokeOperation` único no Dart | Remove 4 blocos `try/catch` idênticos em cada lado. Comportamento igual: retorna `true` e chama `onDeeplinkSent`, ou retorna `false` e chama `onError`. |
| `initDeeplink` delega a `Deeplink.create` | Um só caminho para trocar o handler e reconfigurar a licença, já com tratamento de erro. |
| `scripts/build_android_aar.sh` aceita `GRADLE_ARGS` | Permite `--no-daemon -Dorg.gradle.jvmargs=-Xmx2g` em máquinas com pouca memória. |

As regras de licença, os nomes de prefs, o formato do cache e a API pública Dart não mudaram nessa versão.

**Depois da 1.2.0 (JYN-234, contrato do jylabtech_core):** a primeira validação de cada processo vai ao
servidor mesmo com cache válido; com cache, revalida a cada 4h; recusa definitiva (4xx exceto 408/429,
ou 200 com licença inutilizável) apaga o cache e bloqueia na hora; falha temporária (exceção, timeout,
408/429/5xx, corpo ilegível) usa o cache de no máximo 72h (antes 7 dias) e espera 5 min antes de tentar de
novo. `validateLicence` é `synchronized`; `validateExistsLicence()` (sem uso em produção) só libera quando
não há consulta online devida. Nomes de prefs e formato do cache continuam iguais.

## Testes

| Suite | Antes | Depois |
|---|---|---|
| Nativo JVM (`android/native`, `testReleaseUnitTest`) | 62 | 107 |
| Ponte (`android/src/test`, `:stone_deep_tech:testDebugUnitTest`) | 3 | 5 |
| Dart (`flutter test`) | 16 | 70 |

Cobertura por método do canal (sucesso e erro nos dois lados): `configureLicence`, `sendDeeplink`,
`sendCancel`, `sendPrint` e `sendReprint`, mais o método desconhecido (`notImplemented`) e as chamadas
nativo → Dart (`onDeeplinkResponse`, `onDeeplinkError`).

## Pendências e limitações (decisão do dono)

- **Sem hardware não dá para testar** a abertura real dos apps da Stone (`payment-app`, `cancel-app`, `printer-app`, `reprinter-app`) nem o formato real da URI de retorno. Os testes cobrem até o `Intent`/`DeeplinkRequest`. Vale um teste manual numa maquininha Stone: pagamento, cancelamento, impressão, reimpressão, retorno a frio e rotação durante o retorno.
- **`installmentCount` sem CREDIT:** é enviado ao app da Stone mesmo para débito/PIX, como antes. Avaliar se o Dart deve deixar de enviar.
- **Mesmo scheme para a Stone e para outros deeplinks:** se o app usar o próprio scheme de retorno da Stone para outros deeplinks, esses intents também serão tratados como resposta. Recomenda-se um scheme exclusivo para a Stone.
- **`Deeplink` é singleton estático:** um segundo `StoneDeepTech(outroCanal)` reaproveita o canal da primeira instância. Na prática todo mundo usa `StoneDeepTech.I`.
- **`StoneDeepTech.deeplink` lança `String`** quando não inicializado. Trocar por `StateError` quebraria quem faz `on String catch`.
- **Constantes públicas sem uso interno:** `CHANNEL_NAME` e `USER_REFERENCE` (exportadas). Mantidas por compatibilidade.
- **`onDeeplinkSent` que lança exceção** faz o método retornar `false` e chamar `onError`, embora o app da Stone já tenha sido aberto (comportamento antigo, mantido).
- **Morte do processo depois da entrega:** o extra de "entregue" vive no objeto `Intent` do processo. Se o sistema matar o processo e restaurar a Activity, o intent original volta sem o extra e a resposta pode ser entregue mais uma vez. Deduplicar por URI evitaria isso, mas descartaria respostas legítimas iguais, então ficou assim.
