## 2.0.0

- **BREAKING:** validação de licença e cobrança removidas. O plugin não consulta mais nenhum servidor e não precisa de chave.
  - `StoneDeepTech.initDeeplink`: parâmetros `licenceKey` e `licenceInternalKey` removidos. Agora é `initDeeplink(handler: ...)`.
  - `Deeplink.create`: parâmetros `licenceKey` e `licenceInternalKey` removidos.
  - `Deeplink.updateLicence` removido.
  - Constante `licenceCheck` removida, junto com o módulo `src/licence` e as constantes que ele exportava (`CHANNEL_NAME`, `USER_REFERENCE`). Use `StoneDeepTech.channelName`.
  - Método de canal `configureLicence` removido. Erros `LICENCE_ERROR`, `LICENCE_NOT_CONFIGURED` e `INVALID_ARGUMENT` (da licença) removidos: as operações (`sendDeeplink`, `sendCancel`, `sendPrint`, `sendReprint`) abrem o app da Stone direto.
  - Nativo: removidas as classes de licença (`CheckLicenceTech`, `CheckLicenceEntity`, `LicenceConfig`, `LicenceRules`, `LicenceProvider`, `LicenceRemote`, `LicenceResult`, `LicenceStorage`, `EncryptedLicenceStorage`), o gate por operação e a thread de validação. `build.gradle` não lê mais `licenceKey`/`licenceInternalKey` (env/`local.properties`/`BuildConfig`). Dependência `androidx.security:security-crypto` e permissão `INTERNET` removidas.
- Respostas da Stone recebidas antes do handler (app aberto a frio pelo deeplink de retorno) continuam guardadas: agora são entregues quando o Dart chama o novo método de canal `initDeeplink`, disparado por `StoneDeepTech.initDeeplink`/`Deeplink.create` logo depois de registrar o handler. Falhas nesse aviso vão para `handler.onError`.
- Projeto open source sob a licença MIT.
- README reescrito: requisitos, configuração Android, uso rápido, referência completa da API, exemplos por operação, erros, testes e guia de migração.
- `pubspec.yaml`: descrição em inglês, `repository`, `issue_tracker` e `topics`.
- Example: removidos o `--dart-define` e as constantes da chave de licença.
- Testes atualizados: os de licença foram apagados e os demais passam a verificar que cada operação chama o canal/o app da Stone direto. Binários `android/libs` regenerados.

## 1.3.0

- Licença conforme o contrato do jylabtech_core (JYN-234): o cache offline vale por no máximo 72 horas (antes 7 dias) e só é usado quando o servidor não responde (sem internet, timeout, 408/429, 5xx ou resposta ilegível).
- A primeira operação depois de abrir o app consulta o servidor, e a licença é revalidada a cada 4 horas de uso. Após uma falha de rede, o plugin espera 5 minutos antes de tentar o servidor de novo (usa o cache nesse intervalo).
- Recusa do servidor (cliente bloqueado, licença vencida/cancelada/suspensa, não encontrada, aparelho desativado) apaga o cache e bloqueia na hora. Corpo com `"reason": "customer_blocked"` gera a mensagem "Cliente bloqueado. Entre em contato com a JY Labtech.".
- `CheckLicenceTech.validateExistsLicence()` (atalho só-cache) só devolve `true` quando não há consulta online devida (já validado online nesta execução há menos de 4h, ou servidor fora do ar há menos de 5 min).

## 1.2.0

Validação de licença mais rígida. Licenças vencidas, suspensas, canceladas ou inativas, que antes podiam passar, agora bloqueiam as operações.

- **URL travada no servidor oficial:** a validação sempre usa `https://licence-server.invalid/pos-payments/licence/check`. O `licenceInternalKey` só é aceito se apontar para esse servidor (https, host exato, caminho `/pos-payments/licence/check...`); qualquer outro valor (URL antiga do Cloud Run, outro host, http, userinfo, base64 inválido) é ignorado e o servidor oficial é usado.
- **`licenceInternalKey` opcional** em `initDeeplink`, `Deeplink.create` e `updateLicence` (padrão: `licenceCheck`). Não quebra a API.
- **Só libera com licença válida e ativa:** HTTP 200 com licença vencida, `isExpired`, `isInactive`, `isCanceled`, sem `expiredAt` ou JSON inválido agora é erro e não é salvo no cache. O resultado da validação começa como erro (antes começava como sucesso).
- **Cache offline mais seguro:** só vale com licença utilizável, gravado há no máximo 7 dias, sem data de gravação no futuro (tolerância de 5 min), mesma chave e mesma URL resolvida. Caso contrário é apagado e a licença é revalidada no servidor. Nomes de prefs e formato JSON mantidos (caches gravados com o `licenceInternalKey` do servidor oficial continuam valendo; os do servidor antigo são revalidados uma vez).
- **Toda operação checa a licença** (`sendDeeplink`, `sendCancel`, `sendPrint`, `sendReprint`) antes de abrir o app da Stone; licença inválida responde `LICENCE_ERROR` com a mensagem do servidor.
- **Sem travar a interface:** a consulta ao servidor saiu da main thread (antes usava `future.get()`), com timeouts de conexão e leitura de 15 s. Mensagens de erro úteis: a `message` do servidor ou `Erro ao validar licença (HTTP <código>)`, nunca `null`.
- `IDeepLinkHandler.onDeeplinkSent` agora é chamado só depois que o nativo aceita a operação (licença válida), não antes.
- Testes: JVM do nativo (`android/native/src/test`: URL, regras, parse da resposta do core, decisão HTTP, cache, fluxo e bloqueio das operações), da ponte do plugin (`android/src/test`) e Dart (`test/`). Removido o teste duplicado em `android/src/test/java/com/example`.
- `scripts/build_android_aar.sh`: corrigida a saída prematura quando `JAVA_HOME` já está definido e variáveis não inicializadas com `set -u`. Binários `android/libs` regenerados.
- Validação de licença migrada para o novo serviço da JY Labtech (`https://licence-server.invalid/pos-payments/licence/check`): constante `licenceCheck` e example atualizados. As chaves de licença existentes continuam válidas e o contrato da API não mudou.
- Adicionado `.pubignore` para não publicar keystores, senhas e `local.properties` do example; teste do example corrigido.
- README: nova seção de licença (portal https://portal.jylabtech.com.br, plano debug de cortesia, limite por dispositivo, cache offline de 7 dias, tabela de erros e troubleshooting), dependência do pub.dev e exemplo de tratamento de erros corrigidos (os métodos retornam `false` e chamam `onError`, não lançam exceção).
- **Inteiros grandes:** `amount`, `orderId` e `installmentCount` acima de 2^31 (o Dart envia como Long; por exemplo um `orderId` com timestamp) não fazem mais a operação falhar com `ERROR` por `ClassCastException` no nativo.
- **Resposta da Stone no cold start:** se o app for aberto pela resposta da Stone antes de `initDeeplink`, a resposta fica pendente e é entregue a `onDeeplinkResponse` quando o plugin é inicializado (antes era perdida). Após rotação ou recriação da Activity, a resposta não é mais entregue em duplicidade (o intent entregue é marcado), e o listener de `onNewIntent` passa a ser removido ao desanexar a Activity.
- **Deeplinks do app preservados:** só é tratado como resposta da Stone, e consumido, um `ACTION_VIEW` cujo scheme é o scheme de retorno enviado nas operações (`returnScheme`/`schemeReturn`). Os demais deeplinks do app não são mais repassados a `onDeeplinkResponse` nem consumidos.
- **Erros de configuração da licença:** falhas do nativo no `configureLicence` disparado por `initDeeplink`/`Deeplink.create` agora chamam `handler.onError`, em vez de virar erro assíncrono não tratado. `configureLicence` com tipo inválido responde `INVALID_ARGUMENT`.
- **Sem sucesso falso:** se o plugin estiver desanexado do engine, a operação responde `ERROR` em vez de `true`. Uma tarefa de validação rejeitada pelo executor responde `LICENCE_ERROR` em vez de deixar a `Future` sem resposta.
- **Resposta única garantida** (`SafeResult`) em todas as chamadas do canal. A thread de validação de licença é encerrada em `onDetachedFromEngine`.
- **Desempenho:** o `EncryptedSharedPreferences`/`MasterKey` do cache de licença é criado uma vez e reaproveitado (antes era criado até 3 vezes por validação). Nomes de prefs, formato do cache e regras de licença não mudaram.
- **`PrintResult.fromUri`:** passa a ler primeiro o código dos parâmetros (`result`, `status`, `code`, `print_result`), mantendo a busca antiga como fallback. Um scheme ou host contendo "success" não mascara mais um erro. `fromString` ignora espaços.
- Refatoração interna sem mudança de API: deeplinks montados por `DeeplinkRequests` (lógica pura) e abertos por `StoneGateway`, com código duplicado removido no Dart e no nativo. `scripts/build_android_aar.sh` aceita `GRADLE_ARGS`. Binários `android/libs` regenerados.
- Testes de todos os métodos: nativo 62 → 107, ponte 3 → 5, Dart 16 → 70 (argumentos exatos, sucesso, `PlatformException`, `LICENCE_ERROR`, modelos, enums e parsers). Análise técnica em `doc/ANALISE.md` (fora do pacote publicado).

## 1.1.0

- Fix transaction methods

## 1.0.0

- Initial release
