import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:stone_deep_tech/stone_deep_tech.dart';

const String _envLicenceKey = '';

const String _envLicenceInternalKey =
    '***REMOVIDO***';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Stone Deeplink Demo',
      theme: ThemeData(primarySwatch: Colors.blue, useMaterial3: true),
      home: const DeeplinkPage(),
    );
  }
}

class DeeplinkPage extends StatefulWidget {
  const DeeplinkPage({super.key});

  @override
  State<DeeplinkPage> createState() => _DeeplinkPageState();
}

class _DeeplinkPageState extends State<DeeplinkPage> {
  String _statusMessage = 'Aguardando inicialização...';
  bool _isInitialized = false;
  bool _isLoading = false;

  // Controllers para os campos
  final _amountController = TextEditingController(text: '10000');
  final _orderIdController = TextEditingController();
  final _installmentCountController = TextEditingController();

  // Controllers para cancelamento
  final _cancelAtkController = TextEditingController();
  final _cancelAmountController = TextEditingController();

  // Controllers para reimpressão
  final _reprintAtkController = TextEditingController();

  TransactionType? _selectedTransactionType = TransactionType.debit;
  InstallmentType? _selectedInstallmentType = InstallmentType.none;
  bool _editableAmount = false;
  bool _cancelEditableAmount = false;
  bool _showFeedbackScreen = true;
  bool _reprintShowFeedbackScreen = true;
  TypeCustomer? _selectedTypeCustomer = TypeCustomer.client;

  // Lista de conteúdo para impressão
  final List<PrintContent> _printContents = [];

  String? _lastResponse;
  PrintResult? _lastPrintResult;
  final List<String> _logs = [];

  late final _deepLinkHandler = _MyDeepLinkHandler(_updateStatus, _addLog, _updateResponse);

  void _updateStatus(String message) {
    if (mounted) {
      setState(() {
        _statusMessage = message;
      });
    }
  }

  void _addLog(String log) {
    if (mounted) {
      setState(() {
        _logs.insert(0, '${DateTime.now().toString().substring(11, 19)} - $log');
        if (_logs.length > 20) {
          _logs.removeLast();
        }
      });
    }
  }

  void _updateResponse(String response) {
    if (mounted) {
      setState(() {
        _lastResponse = response;
        // Verificar se é um resultado de impressão
        final printResult = DeeplinkResponseParser.parsePrintResult(response);
        if (printResult != null) {
          _lastPrintResult = printResult;
        } else {
          // Tentar extrair do query parameter
          final resultCode = DeeplinkResponseParser.extractResultCode(response);
          if (resultCode != null) {
            _lastPrintResult = PrintResult.fromString(resultCode);
          } else {
            _lastPrintResult = null;
          }
        }
      });
    }
  }

  @override
  void initState() {
    super.initState();
    _initializeDeeplink();
  }

  @override
  void dispose() {
    _amountController.dispose();
    _orderIdController.dispose();
    _installmentCountController.dispose();
    _cancelAtkController.dispose();
    _cancelAmountController.dispose();
    _reprintAtkController.dispose();
    super.dispose();
  }

  void _initializeDeeplink() {
    try {
      if (_envLicenceKey.isEmpty) {
        throw Exception(
          'Defina as chaves de licença via --dart-define STONE_DEEP_TECH_LICENCE_KEY e STONE_DEEP_TECH_LICENCE_INTERNAL_KEY.',
        );
      }

      StoneDeepTech.I.initDeeplink(
        handler: _deepLinkHandler,
        licenceKey: _envLicenceKey,
        licenceInternalKey: _envLicenceInternalKey,
      );
      setState(() {
        _isInitialized = true;
        _statusMessage = '✓ Plugin inicializado com sucesso!';
      });
      _addLog('Plugin inicializado');
    } catch (e) {
      setState(() {
        _statusMessage = '✗ Erro ao inicializar: $e';
      });
      _addLog('Erro na inicialização: $e');
    }
  }

  Future<void> _sendDeeplink() async {
    if (!_isInitialized) {
      _updateStatus('✗ Plugin não inicializado!');
      return;
    }

    setState(() {
      _isLoading = true;
      _statusMessage = 'Enviando deeplink...';
      _lastResponse = null;
    });

    _addLog('Preparando deeplink...');

    try {
      // Validar e converter valores
      final amountText = _amountController.text.trim();
      if (amountText.isEmpty) {
        throw Exception('Valor não pode estar vazio');
      }

      final amount = int.tryParse(amountText);
      if (amount == null || amount <= 0) {
        throw Exception('Valor inválido. Use um número maior que zero.');
      }

      int? orderId;
      if (_orderIdController.text.trim().isNotEmpty) {
        orderId = int.tryParse(_orderIdController.text.trim());
      }

      int? installmentCount;
      if (_installmentCountController.text.trim().isNotEmpty) {
        installmentCount = int.tryParse(_installmentCountController.text.trim());
        if (installmentCount != null && (installmentCount < 2 || installmentCount > 99)) {
          throw Exception('Número de parcelas deve estar entre 2 e 99');
        }
      }

      final params = DeeplinkParams(
        amount: amount,
        editableAmount: _editableAmount,
        transactionType: _selectedTransactionType,
        installmentCount: installmentCount,
        installmentType: _selectedInstallmentType,
        orderId: orderId,
        returnScheme: 'stone_deep_tech_example',
      );

      _addLog(
        'Enviando deeplink: ${_selectedTransactionType?.value ?? 'N/A'}, R\$ ${(amount / 100).toStringAsFixed(2)}',
      );

      final success = await StoneDeepTech.I.deeplink.sendDeeplink(params);

      if (success) {
        setState(() {
          _statusMessage = '✓ Deeplink enviado com sucesso! Aguardando resposta...';
        });
        _addLog('Deeplink enviado com sucesso');
      } else {
        setState(() {
          _statusMessage = '✗ Erro ao enviar deeplink';
        });
        _addLog('Erro ao enviar deeplink');
      }
    } catch (e) {
      setState(() {
        _statusMessage = '✗ Erro: $e';
      });
      _addLog('Erro: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _sendCancel() async {
    if (!_isInitialized) {
      _updateStatus('✗ Plugin não inicializado!');
      return;
    }

    setState(() {
      _isLoading = true;
      _statusMessage = 'Enviando cancelamento...';
      _lastResponse = null;
    });

    _addLog('Preparando cancelamento...');

    try {
      final atkText = _cancelAtkController.text.trim();
      if (atkText.isEmpty) {
        throw Exception('ATK não pode estar vazio');
      }

      int? amount;
      if (_cancelAmountController.text.trim().isNotEmpty) {
        amount = int.tryParse(_cancelAmountController.text.trim());
        if (amount == null || amount <= 0) {
          throw Exception('Valor inválido. Use um número maior que zero.');
        }
      }

      final params = CancelParams(
        returnScheme: 'stone_deep_tech_example',
        atk: atkText,
        amount: amount,
        editableAmount: _cancelEditableAmount,
      );

      _addLog(
        'Enviando cancelamento: ATK=$atkText${amount != null ? ', Valor=R\$ ${(amount / 100).toStringAsFixed(2)}' : ''}',
      );

      final success = await StoneDeepTech.I.deeplink.sendCancel(params);

      if (success) {
        setState(() {
          _statusMessage = '✓ Cancelamento enviado com sucesso! Aguardando resposta...';
        });
        _addLog('Cancelamento enviado com sucesso');
      } else {
        setState(() {
          _statusMessage = '✗ Erro ao enviar cancelamento';
        });
        _addLog('Erro ao enviar cancelamento');
      }
    } catch (e) {
      setState(() {
        _statusMessage = '✗ Erro: $e';
      });
      _addLog('Erro: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _sendPrint() async {
    if (!_isInitialized) {
      _updateStatus('✗ Plugin não inicializado!');
      return;
    }

    if (_printContents.isEmpty) {
      _updateStatus('✗ Adicione pelo menos um item para impressão!');
      return;
    }

    setState(() {
      _isLoading = true;
      _statusMessage = 'Enviando impressão...';
      _lastResponse = null;
    });

    _addLog('Preparando impressão...');

    try {
      final params = PrintParams(
        schemeReturn: 'stone_deep_tech_example',
        printableContent: _printContents,
        showFeedbackScreen: _showFeedbackScreen,
      );

      _addLog('Enviando impressão com ${_printContents.length} item(ns)');

      final success = await StoneDeepTech.I.deeplink.sendPrint(params);

      if (success) {
        setState(() {
          _statusMessage = '✓ Impressão enviada com sucesso! Aguardando resposta...';
        });
        _addLog('Impressão enviada com sucesso');
      } else {
        setState(() {
          _statusMessage = '✗ Erro ao enviar impressão';
        });
        _addLog('Erro ao enviar impressão');
      }
    } catch (e) {
      setState(() {
        _statusMessage = '✗ Erro: $e';
      });
      _addLog('Erro: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _addPrintItem(PrintContentType type) {
    if (type == PrintContentType.text) {
      _printContents.add(
        TextPrintContent(content: 'Texto exemplo', align: PrintTextAlign.center, size: PrintTextSize.medium),
      );
    } else if (type == PrintContentType.line) {
      _printContents.add(LinePrintContent(content: 'Linha exemplo'));
    } else if (type == PrintContentType.image) {
      // Imagem de exemplo em base64 (1x1 pixel transparente)
      _printContents.add(
        ImagePrintContent(
          imagePath: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
        ),
      );
    }
    setState(() {});
    _addLog('Item de impressão adicionado: ${type.value}');
  }

  void _removePrintItem(int index) {
    if (index >= 0 && index < _printContents.length) {
      _printContents.removeAt(index);
      setState(() {});
      _addLog('Item de impressão removido');
    }
  }

  void _clearPrintItems() {
    _printContents.clear();
    setState(() {});
    _addLog('Itens de impressão limpos');
  }

  Future<void> _sendReprint() async {
    if (!_isInitialized) {
      _updateStatus('✗ Plugin não inicializado!');
      return;
    }

    setState(() {
      _isLoading = true;
      _statusMessage = 'Enviando reimpressão...';
      _lastResponse = null;
    });

    _addLog('Preparando reimpressão...');

    try {
      final atkText = _reprintAtkController.text.trim();
      if (atkText.isEmpty) {
        throw Exception('ATK não pode estar vazio');
      }

      if (_selectedTypeCustomer == null) {
        throw Exception('Selecione o tipo de cliente');
      }

      final params = ReprintParams(
        schemeReturn: 'stone_deep_tech_example',
        atk: atkText,
        typeCustomer: _selectedTypeCustomer!,
        showFeedbackScreen: _reprintShowFeedbackScreen,
      );

      _addLog('Enviando reimpressão: ATK=$atkText, Tipo=${_selectedTypeCustomer!.value}');

      final success = await StoneDeepTech.I.deeplink.sendReprint(params);

      if (success) {
        setState(() {
          _statusMessage = '✓ Reimpressão enviada com sucesso! Aguardando resposta...';
        });
        _addLog('Reimpressão enviada com sucesso');
      } else {
        setState(() {
          _statusMessage = '✗ Erro ao enviar reimpressão';
        });
        _addLog('Erro ao enviar reimpressão');
      }
    } catch (e) {
      setState(() {
        _statusMessage = '✗ Erro: $e';
      });
      _addLog('Erro: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Stone Deeplink Demo'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _isLoading ? null : _initializeDeeplink,
            tooltip: 'Reinicializar plugin',
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Status Card
            Card(
              color: _isInitialized ? Colors.green.shade50 : Colors.red.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          _isInitialized ? Icons.check_circle : Icons.error,
                          color: _isInitialized ? Colors.green : Colors.red,
                        ),
                        const SizedBox(width: 8),
                        const Text('Status do Plugin', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(_statusMessage, style: const TextStyle(fontSize: 14)),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Formulário de Configuração
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Configuração do Deeplink', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 16),

                    // Valor
                    TextField(
                      controller: _amountController,
                      decoration: const InputDecoration(
                        labelText: 'Valor (em centavos)',
                        hintText: 'Ex: 10000 = R\$ 100,00',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.attach_money),
                      ),
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    ),
                    const SizedBox(height: 16),

                    // Tipo de Transação
                    const Text('Tipo de Transação:', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    SegmentedButton<TransactionType>(
                      segments: const [
                        ButtonSegment(value: TransactionType.debit, label: Text('Débito')),
                        ButtonSegment(value: TransactionType.credit, label: Text('Crédito')),
                        ButtonSegment(value: TransactionType.voucher, label: Text('Voucher')),
                        ButtonSegment(value: TransactionType.instantPayment, label: Text('Instant')),
                        ButtonSegment(value: TransactionType.pix, label: Text('PIX')),
                      ],
                      selected: _selectedTransactionType != null ? {_selectedTransactionType!} : {},
                      onSelectionChanged: (Set<TransactionType> selection) {
                        setState(() {
                          _selectedTransactionType = selection.isNotEmpty ? selection.first : null;
                        });
                      },
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _selectedTransactionType = null;
                        });
                      },
                      child: const Text('Limpar seleção (nulo)'),
                    ),
                    const SizedBox(height: 16),

                    // Valor Editável
                    SwitchListTile(
                      title: const Text('Permitir edição do valor'),
                      subtitle: const Text('O usuário pode alterar o valor no app de pagamento'),
                      value: _editableAmount,
                      onChanged: (value) {
                        setState(() {
                          _editableAmount = value;
                        });
                      },
                    ),
                    const SizedBox(height: 16),

                    // Parcelamento
                    TextField(
                      controller: _installmentCountController,
                      decoration: const InputDecoration(
                        labelText: 'Número de Parcelas (opcional)',
                        hintText: 'Entre 2 e 99',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.credit_card),
                      ),
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    ),
                    const SizedBox(height: 8),
                    DropdownButtonFormField<InstallmentType>(
                      decoration: const InputDecoration(
                        labelText: 'Tipo de Parcelamento (default: NONE - à vista)',
                        border: OutlineInputBorder(),
                      ),
                      value: _selectedInstallmentType,
                      items: const [
                        DropdownMenuItem(value: InstallmentType.none, child: Text('NONE (à vista)')),
                        DropdownMenuItem(value: InstallmentType.merchant, child: Text('MERCHANT (sem juros)')),
                        DropdownMenuItem(value: InstallmentType.issuer, child: Text('ISSUER (com juros)')),
                      ],
                      onChanged: (value) {
                        setState(() {
                          _selectedInstallmentType = value ?? InstallmentType.none;
                        });
                      },
                    ),
                    const SizedBox(height: 16),

                    // Order ID
                    TextField(
                      controller: _orderIdController,
                      decoration: const InputDecoration(
                        labelText: 'Order ID (opcional)',
                        hintText: 'ID do pedido',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.tag),
                      ),
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    ),
                    const SizedBox(height: 24),

                    // Botão Enviar
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: ElevatedButton.icon(
                        onPressed: _isLoading || !_isInitialized ? null : _sendDeeplink,
                        icon: _isLoading
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.send),
                        label: Text(_isLoading ? 'Enviando...' : 'Enviar Deeplink'),
                        style: ElevatedButton.styleFrom(textStyle: const TextStyle(fontSize: 16)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Seção de Cancelamento
            Card(
              color: Colors.orange.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Cancelamento', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 16),

                    // ATK
                    TextField(
                      controller: _cancelAtkController,
                      decoration: const InputDecoration(
                        labelText: 'ATK (Authorization Transaction Key) *',
                        hintText: 'Chave da transação a ser cancelada',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.key),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Valor do Cancelamento
                    TextField(
                      controller: _cancelAmountController,
                      decoration: const InputDecoration(
                        labelText: 'Valor (em centavos, opcional)',
                        hintText: 'Ex: 10000 = R\$ 100,00',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.attach_money),
                      ),
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    ),
                    const SizedBox(height: 16),

                    // Valor Editável
                    SwitchListTile(
                      title: const Text('Permitir edição do valor'),
                      subtitle: const Text('O usuário pode alterar o valor no app de pagamento'),
                      value: _cancelEditableAmount,
                      onChanged: (value) {
                        setState(() {
                          _cancelEditableAmount = value;
                        });
                      },
                    ),
                    const SizedBox(height: 24),

                    // Botão Cancelar
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: ElevatedButton.icon(
                        onPressed: _isLoading || !_isInitialized ? null : _sendCancel,
                        icon: _isLoading
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.cancel),
                        label: Text(_isLoading ? 'Enviando...' : 'Enviar Cancelamento'),
                        style: ElevatedButton.styleFrom(
                          textStyle: const TextStyle(fontSize: 16),
                          backgroundColor: Colors.orange,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Seção de Impressão
            Card(
              color: Colors.purple.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Impressão Customizada', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 16),

                    // Itens de impressão
                    if (_printContents.isNotEmpty) ...[
                      const Text('Itens para impressão:', style: TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 8),
                      ...List.generate(_printContents.length, (index) {
                        final item = _printContents[index];
                        return Card(
                          margin: const EdgeInsets.only(bottom: 8),
                          child: ListTile(
                            title: Text('${item.type.value.toUpperCase()}'),
                            subtitle: Text(
                              item is TextPrintContent
                                  ? '${item.content} (${item.align.value}, ${item.size.value})'
                                  : item is LinePrintContent
                                  ? item.content
                                  : 'Imagem Base64',
                            ),
                            trailing: IconButton(
                              icon: const Icon(Icons.delete, color: Colors.red),
                              onPressed: () => _removePrintItem(index),
                            ),
                          ),
                        );
                      }),
                      const SizedBox(height: 8),
                    ],

                    // Botões para adicionar itens
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        ElevatedButton.icon(
                          onPressed: _isLoading ? null : () => _addPrintItem(PrintContentType.text),
                          icon: const Icon(Icons.text_fields),
                          label: const Text('Adicionar Texto'),
                        ),
                        ElevatedButton.icon(
                          onPressed: _isLoading ? null : () => _addPrintItem(PrintContentType.line),
                          icon: const Icon(Icons.remove),
                          label: const Text('Adicionar Linha'),
                        ),
                        ElevatedButton.icon(
                          onPressed: _isLoading ? null : () => _addPrintItem(PrintContentType.image),
                          icon: const Icon(Icons.image),
                          label: const Text('Adicionar Imagem'),
                        ),
                        if (_printContents.isNotEmpty)
                          OutlinedButton.icon(
                            onPressed: _isLoading ? null : _clearPrintItems,
                            icon: const Icon(Icons.clear),
                            label: const Text('Limpar Tudo'),
                          ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Show Feedback Screen
                    SwitchListTile(
                      title: const Text('Mostrar tela de feedback'),
                      subtitle: const Text('Mostrar tela de sucesso/erro no final do fluxo'),
                      value: _showFeedbackScreen,
                      onChanged: (value) {
                        setState(() {
                          _showFeedbackScreen = value;
                        });
                      },
                    ),
                    const SizedBox(height: 24),

                    // Botão Imprimir
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: ElevatedButton.icon(
                        onPressed: _isLoading || !_isInitialized || _printContents.isEmpty ? null : _sendPrint,
                        icon: _isLoading
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.print),
                        label: Text(_isLoading ? 'Enviando...' : 'Enviar Impressão'),
                        style: ElevatedButton.styleFrom(
                          textStyle: const TextStyle(fontSize: 16),
                          backgroundColor: Colors.purple,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Seção de Reimpressão
            Card(
              color: Colors.blue.shade50,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Reimpressão', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 16),

                    // ATK
                    TextField(
                      controller: _reprintAtkController,
                      decoration: const InputDecoration(
                        labelText: 'ATK (Authorization Transaction Key) *',
                        hintText: 'Chave da transação a ser reimpressa',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.key),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Tipo de Cliente
                    DropdownButtonFormField<TypeCustomer>(
                      value: _selectedTypeCustomer,
                      decoration: const InputDecoration(
                        labelText: 'Tipo de Cliente *',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.person),
                      ),
                      items: TypeCustomer.values.map((type) {
                        return DropdownMenuItem(
                          value: type,
                          child: Text(type == TypeCustomer.client ? 'Cliente (CLIENT)' : 'Lojista (MERCHANT)'),
                        );
                      }).toList(),
                      onChanged: (value) {
                        setState(() {
                          _selectedTypeCustomer = value;
                        });
                      },
                    ),
                    const SizedBox(height: 16),

                    // Show Feedback Screen
                    SwitchListTile(
                      title: const Text('Mostrar tela de feedback'),
                      subtitle: const Text('Mostrar tela de sucesso/erro no final do fluxo'),
                      value: _reprintShowFeedbackScreen,
                      onChanged: (value) {
                        setState(() {
                          _reprintShowFeedbackScreen = value;
                        });
                      },
                    ),
                    const SizedBox(height: 24),

                    // Botão Reimprimir
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: ElevatedButton.icon(
                        onPressed: _isLoading || !_isInitialized ? null : _sendReprint,
                        icon: _isLoading
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.print),
                        label: Text(_isLoading ? 'Enviando...' : 'Enviar Reimpressão'),
                        style: ElevatedButton.styleFrom(
                          textStyle: const TextStyle(fontSize: 16),
                          backgroundColor: Colors.blue,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Resposta Recebida
            if (_lastResponse != null) ...[
              Card(
                color: _lastPrintResult == PrintResult.success
                    ? Colors.green.shade50
                    : (_lastPrintResult != null ? Colors.red.shade50 : Colors.blue.shade50),
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            _lastPrintResult == PrintResult.success
                                ? Icons.check_circle
                                : (_lastPrintResult != null ? Icons.error : Icons.check_circle),
                            color: _lastPrintResult == PrintResult.success
                                ? Colors.green
                                : (_lastPrintResult != null ? Colors.red : Colors.green),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            _lastPrintResult != null ? 'Resultado da Impressão' : 'Resposta Recebida',
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      if (_lastPrintResult != null) ...[
                        const SizedBox(height: 8),
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: _lastPrintResult == PrintResult.success
                                ? Colors.green.shade100
                                : Colors.red.shade100,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Código: ${_lastPrintResult!.value}',
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                              ),
                              const SizedBox(height: 4),
                              Text(_lastPrintResult!.description, style: const TextStyle(fontSize: 13)),
                            ],
                          ),
                        ),
                        const SizedBox(height: 8),
                      ],
                      const SizedBox(height: 8),
                      const Text('URI Completa:', style: TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 4),
                      SelectableText(_lastResponse!, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                      const SizedBox(height: 8),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton.icon(
                          onPressed: () {
                            Clipboard.setData(ClipboardData(text: _lastResponse!));
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Resposta copiada para a área de transferência')),
                            );
                          },
                          icon: const Icon(Icons.copy),
                          label: const Text('Copiar Resposta'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Logs
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text('Logs', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        TextButton.icon(
                          onPressed: () {
                            setState(() {
                              _logs.clear();
                            });
                          },
                          icon: const Icon(Icons.clear, size: 18),
                          label: const Text('Limpar'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Container(
                      height: 200,
                      decoration: BoxDecoration(color: Colors.grey.shade900, borderRadius: BorderRadius.circular(8)),
                      padding: const EdgeInsets.all(12),
                      child: _logs.isEmpty
                          ? const Center(
                              child: Text('Nenhum log ainda', style: TextStyle(color: Colors.grey)),
                            )
                          : ListView.builder(
                              reverse: true,
                              itemCount: _logs.length,
                              itemBuilder: (context, index) {
                                return Padding(
                                  padding: const EdgeInsets.only(bottom: 4),
                                  child: Text(
                                    _logs[index],
                                    style: const TextStyle(
                                      color: Colors.greenAccent,
                                      fontFamily: 'monospace',
                                      fontSize: 12,
                                    ),
                                  ),
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MyDeepLinkHandler implements IDeepLinkHandler {
  final Function(String) onStatusUpdate;
  final Function(String) onLog;
  final Function(String) onResponse;

  _MyDeepLinkHandler(this.onStatusUpdate, this.onLog, this.onResponse);

  @override
  Future<void> onDeeplinkResponse(String responseUri) async {
    debugPrint('Resposta do deeplink recebida: $responseUri');
    onLog('Resposta recebida: $responseUri');

    // Verificar se é resultado de impressão
    final printResult = DeeplinkResponseParser.parsePrintResult(responseUri);
    if (printResult != null) {
      onLog('Resultado da impressão: ${printResult.value} - ${printResult.description}');
      if (printResult == PrintResult.success) {
        onStatusUpdate('✓ Impressão realizada com sucesso!');
      } else {
        onStatusUpdate('✗ Erro na impressão: ${printResult.description}');
      }
    } else {
      // Tentar extrair do query parameter
      final resultCode = DeeplinkResponseParser.extractResultCode(responseUri);
      if (resultCode != null) {
        final parsedResult = PrintResult.fromString(resultCode);
        if (parsedResult != null) {
          onLog('Resultado da impressão: ${parsedResult.value} - ${parsedResult.description}');
          if (parsedResult == PrintResult.success) {
            onStatusUpdate('✓ Impressão realizada com sucesso!');
          } else {
            onStatusUpdate('✗ Erro na impressão: ${parsedResult.description}');
          }
        } else {
          onStatusUpdate('✓ Resposta recebida! Verifique abaixo.');
        }
      } else {
        onStatusUpdate('✓ Resposta recebida! Verifique abaixo.');
      }
    }

    onResponse(responseUri);
  }

  @override
  Future<void> onError(String message) async {
    debugPrint('Erro no deeplink: $message');
    onLog('Erro: $message');
    onStatusUpdate('✗ Erro: $message');
  }

  @override
  Future<void> onDeeplinkSent() async {
    debugPrint('Deeplink enviado com sucesso');
    onLog('Deeplink enviado com sucesso');
  }
}
