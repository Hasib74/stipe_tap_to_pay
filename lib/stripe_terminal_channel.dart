/*
// import 'package:flutter/services.dart';
//
// class StripeTerminalChannel {
//   // Define the platform channel name (must match native code)
//   static const MethodChannel _channel =
//   MethodChannel('com.example/stripe_terminal');
//
//   /// Start Tap to Pay reader discovery and connect to first available reader
//   static Future<String?> startTapToPay() async {
//     try {
//       final result = await _channel.invokeMethod<String>('startTapToPay');
//       return result;
//     } on PlatformException catch (e) {
//       return "Failed: ${e.message}";
//     }
//   }
//
//   /// Collect a payment from the connected reader
//   static Future<String?> collectPayment({required int amount}) async {
//     try {
//       final result = await _channel.invokeMethod<String>(
//         'collectPayment',
//         {'amount': amount},
//       );
//       return result;
//     } on PlatformException catch (e) {
//       return "Payment failed: ${e.message}";
//     }
//   }
// }
*/


import 'package:flutter/services.dart';

class StripeTerminalChannel {
  static const MethodChannel _channel =
  MethodChannel('com.example/stripe_terminal');

  static Future<String?> startTapToPay() async {
    try {
      final result = await _channel.invokeMethod<String>('startTapToPay');
      return result;
    } on PlatformException catch (e) {
      print('Error: ${e.message}');
      return null;
    }
  }
}