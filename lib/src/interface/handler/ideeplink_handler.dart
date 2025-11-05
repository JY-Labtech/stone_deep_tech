/// Contract deeplink handler
abstract class IDeepLinkHandler {
  /// Called when deeplink response is received
  Future<void> onDeeplinkResponse(String responseUri);

  /// Called when an error occurs
  Future<void> onError(String message);

  /// Called when deeplink is sent successfully
  Future<void> onDeeplinkSent();
}
