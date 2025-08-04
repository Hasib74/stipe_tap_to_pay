import 'package:flutter/material.dart';
import 'package:stripe_tap_to_pay/stripe_terminal_channel.dart';
import 'package:stripe_tap_to_pay/tab_to_pay_screen.dart';

import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Stripe Terminal Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: TerminalScreen(),
    );
  }
}

