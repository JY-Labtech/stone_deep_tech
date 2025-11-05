import 'dart:convert';

/// Content types for custom printing
enum PrintContentType {
  text,
  line,
  image;

  String get value {
    switch (this) {
      case PrintContentType.text:
        return 'text';
      case PrintContentType.line:
        return 'line';
      case PrintContentType.image:
        return 'image';
    }
  }
}

/// Text alignment options
enum PrintTextAlign {
  center,
  right,
  left;

  String get value {
    switch (this) {
      case PrintTextAlign.center:
        return 'center';
      case PrintTextAlign.right:
        return 'right';
      case PrintTextAlign.left:
        return 'left';
    }
  }
}

/// Text size options
enum PrintTextSize {
  big,
  medium,
  small;

  String get value {
    switch (this) {
      case PrintTextSize.big:
        return 'big';
      case PrintTextSize.medium:
        return 'medium';
      case PrintTextSize.small:
        return 'small';
    }
  }
}

/// Base class for print content
abstract class PrintContent {
  final PrintContentType type;

  PrintContent(this.type);

  Map<String, dynamic> toJson();
}

/// Text content for printing
class TextPrintContent extends PrintContent {
  /// Text that will be printed
  final String content;

  /// Text alignment (only for custom text)
  final PrintTextAlign align;

  /// Text size (only for custom text)
  final PrintTextSize size;

  TextPrintContent({
    required this.content,
    required this.align,
    required this.size,
  }) : super(PrintContentType.text);

  @override
  Map<String, dynamic> toJson() {
    return {
      'type': type.value,
      'content': content,
      'align': align.value,
      'size': size.value,
    };
  }
}

/// Line content for printing
class LinePrintContent extends PrintContent {
  /// Text without customization
  final String content;

  LinePrintContent({
    required this.content,
  }) : super(PrintContentType.line);

  @override
  Map<String, dynamic> toJson() {
    return {
      'type': type.value,
      'content': content,
    };
  }
}

/// Image content for printing
class ImagePrintContent extends PrintContent {
  /// Image in Base64 format
  final String imagePath;

  ImagePrintContent({
    required this.imagePath,
  }) : super(PrintContentType.image);

  @override
  Map<String, dynamic> toJson() {
    return {
      'type': type.value,
      'imagePath': imagePath,
    };
  }
}

/// Model for custom print deeplink parameters
class PrintParams {
  /// Determina para onde será o retorno do deeplink.
  /// Deve ser o mesmo valor configurado no lugar da variável scheme no AndroidManifest
  final String schemeReturn;

  /// List of print content items
  final List<PrintContent> printableContent;

  /// Show or not show the success/error screen at the end of the flow
  final bool showFeedbackScreen;

  PrintParams({
    required this.schemeReturn,
    required this.printableContent,
    this.showFeedbackScreen = true,
  });

  Map<String, dynamic> toMap() {
    final contentJson = printableContent.map((content) => content.toJson()).toList();

    return {
      'schemeReturn': schemeReturn,
      'printableContent': jsonEncode(contentJson),
      'showFeedbackScreen': showFeedbackScreen,
    };
  }
}
