/// Customer type for reprint
enum TypeCustomer {
  /// Client receipt
  client,

  /// Merchant receipt
  merchant;

  String get value {
    switch (this) {
      case TypeCustomer.client:
        return 'CLIENT';
      case TypeCustomer.merchant:
        return 'MERCHANT';
    }
  }
}

/// Model for reprint deeplink parameters
class ReprintParams {
  /// Determina para onde será o retorno do deeplink.
  /// Deve ser o mesmo valor configurado no lugar da variável scheme no AndroidManifest
  final String schemeReturn;

  /// Código único da transação gerado pelo autorizador da Stone
  final String atk;

  /// Tipo da reimpressão: CLIENT (cliente) ou MERCHANT (lojista)
  final TypeCustomer typeCustomer;

  /// Show or not show the success/error screen at the end of the flow
  final bool showFeedbackScreen;

  ReprintParams({
    required this.schemeReturn,
    required this.atk,
    required this.typeCustomer,
    this.showFeedbackScreen = true,
  });

  Map<String, dynamic> toMap() {
    return {
      'schemeReturn': schemeReturn,
      'atk': atk,
      'typeCustomer': typeCustomer.value,
      'showFeedbackScreen': showFeedbackScreen,
    };
  }
}
