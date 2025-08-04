// import 'package:flutter/material.dart';
// import 'stripe_terminal_channel.dart';
//
// import 'package:flutter/material.dart';
// import 'stripe_terminal_channel.dart';
//
// class TapToPayScreen extends StatefulWidget {
//   const TapToPayScreen({Key? key}) : super(key: key);
//
//   @override
//   State<TapToPayScreen> createState() => _TapToPayScreenState();
// }
//
// class _TapToPayScreenState extends State<TapToPayScreen> {
//   String _status = "Idle";
//
//   Future<void> _startTapToPay() async {
//     setState(() => _status = "Starting Tap to Pay...");
//     final result = await StripeTerminalChannel.startTapToPay();
//     setState(() => _status = result ?? "No response from native");
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       appBar: AppBar(title: const Text('Tap to Pay Demo')),
//       body: Center(
//         child: Column(
//           mainAxisAlignment: MainAxisAlignment.center,
//           children: [
//             Text("Status: $_status",
//                 style: const TextStyle(fontSize: 16),
//                 textAlign: TextAlign.center),
//             const SizedBox(height: 20),
//             ElevatedButton(
//               onPressed: _startTapToPay,
//               child: const Text("Start Tap to Pay"),
//             ),
//           ],
//         ),
//       ),
//     );
//   }
// }
//
