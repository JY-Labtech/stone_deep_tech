/// Print result codes from Stone printer
enum PrintResult {
  /// Print was successful
  success,

  /// Printer is out of paper or paper tray is open
  printerOutOfPaper,

  /// Error initializing printer
  printerInitError,

  /// Low battery/energy
  printerLowEnergy,

  /// Printer is busy (already printing)
  printerBusy,

  /// Some format sent doesn't match text, image or custom text pattern
  printerUnsupportedFormat,

  /// Maximum buffer limit exceeded
  printerInvalidData,

  /// Printer overheating
  printerOverheating,

  /// Paper jammed in paper tray
  printerPaperJam,

  /// Generic printer error
  printerPrintError;

  String get value {
    switch (this) {
      case PrintResult.success:
        return 'SUCCESS';
      case PrintResult.printerOutOfPaper:
        return 'PRINTER_OUT_OF_PAPER';
      case PrintResult.printerInitError:
        return 'PRINTER_INIT_ERROR';
      case PrintResult.printerLowEnergy:
        return 'PRINTER_LOW_ENERGY';
      case PrintResult.printerBusy:
        return 'PRINTER_BUSY';
      case PrintResult.printerUnsupportedFormat:
        return 'PRINTER_UNSUPPORTED_FORMAT';
      case PrintResult.printerInvalidData:
        return 'PRINTER_INVALID_DATA';
      case PrintResult.printerOverheating:
        return 'PRINTER_OVERHEATING';
      case PrintResult.printerPaperJam:
        return 'PRINTER_PAPER_JAM';
      case PrintResult.printerPrintError:
        return 'PRINTER_PRINT_ERROR';
    }
  }

  String get description {
    switch (this) {
      case PrintResult.success:
        return 'Impressão realizada com sucesso';
      case PrintResult.printerOutOfPaper:
        return 'Impressora sem papel ou com a tampa de bobina aberta';
      case PrintResult.printerInitError:
        return 'Erro ao inicializar a impressora';
      case PrintResult.printerLowEnergy:
        return 'Máquina com baixa energia';
      case PrintResult.printerBusy:
        return 'Impressora ocupada, ocorre quando já está imprimindo algo';
      case PrintResult.printerUnsupportedFormat:
        return 'Algum formato enviado não corresponde ao padrão de texto, imagem ou texto customizado';
      case PrintResult.printerInvalidData:
        return 'Limite máximo do buffer foi ultrapassado';
      case PrintResult.printerOverheating:
        return 'Superaquecimento da impressora';
      case PrintResult.printerPaperJam:
        return 'Papel preso na caixa de bobina';
      case PrintResult.printerPrintError:
        return 'Erro genérico da impressora';
    }
  }

  /// Parse result code from URI string.
  ///
  /// Usa primeiro o código dos parâmetros da URI (`result`, `status`, `code`,
  /// `print_result`) e, se não houver um código conhecido, procura o código em
  /// qualquer parte da URI (comportamento anterior). Assim um scheme ou host
  /// que contenha "success" não mascara um erro informado no parâmetro.
  static PrintResult? fromUri(String uri) {
    final code = DeeplinkResponseParser.extractResultCode(uri);
    final fromParameter = code == null ? null : fromString(code);
    if (fromParameter != null) return fromParameter;

    final uriLower = uri.toLowerCase();
    for (final result in PrintResult.values) {
      if (uriLower.contains(result.value.toLowerCase())) {
        return result;
      }
    }
    return null;
  }

  /// Parse result code from result string (sem diferenciar maiúsculas/minúsculas
  /// e ignorando espaços nas pontas).
  static PrintResult? fromString(String result) {
    final normalized = result.trim().toUpperCase();
    for (final value in PrintResult.values) {
      if (value.value == normalized) return value;
    }
    return null;
  }
}

/// Helper class to parse deeplink responses
class DeeplinkResponseParser {
  /// Parse print result from response URI
  static PrintResult? parsePrintResult(String responseUri) {
    return PrintResult.fromUri(responseUri);
  }

  /// Check if response is a print result
  static bool isPrintResult(String responseUri) {
    return PrintResult.fromUri(responseUri) != null;
  }

  /// Extract result code from URI query parameters
  static String? extractResultCode(String responseUri) {
    try {
      final uri = Uri.parse(responseUri);
      // Try common query parameter names
      return uri.queryParameters['result'] ??
          uri.queryParameters['status'] ??
          uri.queryParameters['code'] ??
          uri.queryParameters['print_result'];
    } catch (e) {
      return null;
    }
  }
}
