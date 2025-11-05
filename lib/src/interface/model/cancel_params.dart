/// Model for cancellation deeplink parameters
class CancelParams {
  /// Determina para onde será o retorno do deeplink.
  /// Deve ser o mesmo valor configurado no lugar da variável scheme no AndroidManifest
  final String returnScheme;

  /// Código único da transação gerado pelo autorizador da Stone
  final String atk;

  /// Amount in cents (e.g., 100 = R$ 1,00)
  final int? amount;

  /// Whether the amount can be edited in the payment app
  final bool editableAmount;

  CancelParams({
    required this.returnScheme,
    required this.atk,
    this.amount,
    this.editableAmount = false,
  });

  Map<String, dynamic> toMap() {
    final map = <String, dynamic>{
      'returnScheme': returnScheme,
      'atk': atk,
      'editableAmount': editableAmount,
    };

    if (amount != null) {
      map['amount'] = amount;
    }

    return map;
  }
}
