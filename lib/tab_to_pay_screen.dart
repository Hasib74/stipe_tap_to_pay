import 'package:flutter/material.dart';
import 'stripe_terminal_channel.dart';

class TapToPayScreen extends StatefulWidget {
  const TapToPayScreen({super.key});

  @override
  State<TapToPayScreen> createState() => _TapToPayScreenState();
}

class _TapToPayScreenState extends State<TapToPayScreen> {
  String _status = "Idle";

  void _startTapToPay() async {
    setState(() => _status = "Discovering reader...");
    final result = await StripeTerminalChannel.startTapToPay();
    setState(() => _status = result ?? "No result");
  }

  void _collectPayment() async {
    setState(() => _status = "Processing payment...");
    // Example: collect $5.00 -> amount in cents
    final result = await StripeTerminalChannel.collectPayment(amount: 500);
    setState(() => _status = result ?? "No result");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tap to Pay Demo')),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(_status, style: const TextStyle(fontSize: 16)),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _startTapToPay,
              child: const Text("Connect to Reader"),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _collectPayment,
              child: const Text("Collect Payment \$5"),
            ),
          ],
        ),
      ),
    );
  }
}
