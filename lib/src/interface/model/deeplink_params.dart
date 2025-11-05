/// Transaction type for Stone payment deeplink
enum TransactionType {
  /// Debit transaction
  debit,

  /// Credit transaction
  credit,

  /// Voucher transaction
  voucher,

  /// Instant payment transaction
  instantPayment,

  /// PIX transaction
  pix;

  /// Convert to string value expected by Stone API
  String get value {
    switch (this) {
      case TransactionType.debit:
        return 'DEBIT';
      case TransactionType.credit:
        return 'CREDIT';
      case TransactionType.voucher:
        return 'VOUCHER';
      case TransactionType.instantPayment:
        return 'INSTANT_PAYMENT';
      case TransactionType.pix:
        return 'PIX';
    }
  }
}

/// Installment type for Stone payment deeplink
enum InstallmentType {
  /// Merchant installment
  merchant,

  /// Issuer installment
  issuer,

  /// No installment
  none;

  /// Convert to string value expected by Stone API
  String get value {
    switch (this) {
      case InstallmentType.merchant:
        return 'MERCHANT';
      case InstallmentType.issuer:
        return 'ISSUER';
      case InstallmentType.none:
        return 'NONE';
    }
  }
}

/// Model for deeplink parameters
class DeeplinkParams {
  /// Amount in cents (e.g., 100 = R$ 1,00)
  final int? amount;

  /// Whether the amount can be edited in the payment app
  final bool editableAmount;

  /// Transaction type: DEBIT, CREDIT, VOUCHER, INSTANT_PAYMENT, or PIX (nullable)
  final TransactionType? transactionType;

  /// Number of installments (2 to 99)
  final int? installmentCount;

  /// Installment type: MERCHANT (parcelado sem juros), ISSUER (parcelado com juros), or NONE (à vista)
  /// Defaults to NONE (à vista)
  final InstallmentType? installmentType;

  /// Order ID for tracking
  final int? orderId;

  /// Determina para onde será o retorno do deeplink.
  /// Deve ser o mesmo valor configurado no lugar da variável scheme no AndroidManifest
  final String returnScheme;

  DeeplinkParams({
    this.amount,
    this.editableAmount = false,
    this.transactionType,
    this.installmentCount,
    this.installmentType = InstallmentType.none,
    this.orderId,
    required this.returnScheme,
  });

  Map<String, dynamic> toMap() {
    final map = <String, dynamic>{
      'editableAmount': editableAmount,
      'returnScheme': returnScheme,
    };

    if (amount != null) {
      map['amount'] = amount;
    }
    if (transactionType != null) {
      map['transactionType'] = transactionType!.value;
    }
    if (installmentCount != null) {
      map['installmentCount'] = installmentCount;
    }
    // installmentType só deve ser enviado se transactionType for CREDIT
    if (installmentType != null && transactionType == TransactionType.credit) {
      map['installmentType'] = installmentType!.value;
    }
    if (orderId != null) {
      map['orderId'] = orderId;
    }

    return map;
  }
}
