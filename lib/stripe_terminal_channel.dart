import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class StripeTerminalService {
  static const MethodChannel _channel = MethodChannel('com.example.your_app_name/stripe_terminal');
  static List<Map<String, dynamic>> _discoveredReaders = [];

  // Add listeners for events from the native side
  static void initialize(Function(List<Map<String, dynamic>>) onReadersDiscovered) {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onReadersDiscovered':
          final readers = List<Map<String, dynamic>>.from(call.arguments);
          _discoveredReaders = readers;
          onReadersDiscovered(readers);
          break;
      }
      return null;
    });
  }

  static Future<String> initializeTerminal() async {
    try {
      final result = await _channel.invokeMethod('initializeTerminal');
      return result;
    } on PlatformException catch (e) {
      throw 'Failed to initialize Terminal: ${e.message}';
    }
  }

  static Future<String> discoverReaders({String discoveryMethod = 'bluetooth'}) async {
    try {
      final result = await _channel.invokeMethod('discoverReaders', {
        'discoveryMethod': discoveryMethod
      });
      return result;
    } on PlatformException catch (e) {
      throw 'Failed to discover readers: ${e.message}';
    }
  }

  static Future<String> connectToReader(String readerId) async {
    try {
      final result = await _channel.invokeMethod('connectToReader', {
        'readerId': readerId
      });
      return result;
    } on PlatformException catch (e) {
      throw 'Failed to connect to reader: ${e.message}';
    }
  }

  static Future<String> createPaymentIntent(int amount, String currency) async {
    try {
      final result = await _channel.invokeMethod('createPaymentIntent', {
        'amount': amount,
        'currency': currency
      });
      return result;
    } on PlatformException catch (e) {
      throw 'Failed to create payment intent: ${e.message}';
    }
  }

  static Future<String> collectPayment(String paymentIntentId) async {
    try {
      final result = await _channel.invokeMethod('collectPayment', {
        'paymentIntentId': paymentIntentId
      });
      return result;
    } on PlatformException catch (e) {
      throw 'Failed to collect payment: ${e.message}';
    }
  }

  static List<Map<String, dynamic>> getDiscoveredReaders() {
    return _discoveredReaders;
  }
}

class TerminalScreen extends StatefulWidget {
  @override
  _TerminalScreenState createState() => _TerminalScreenState();
}

class _TerminalScreenState extends State<TerminalScreen> {
  String _statusMessage = 'Not initialized';
  List<Map<String, dynamic>> _readers = [];

  @override
  void initState() {
    super.initState();
    _setupTerminal();
  }

  Future<void> _setupTerminal() async {
    // Set up the listener for discovered readers
    StripeTerminalService.initialize((readers) {
      setState(() {
        _readers = readers;
        _statusMessage = 'Found ${readers.length} readers';
      });
    });

    try {
      final result = await StripeTerminalService.initializeTerminal();
      setState(() {
        _statusMessage = 'Terminal initialized: $result';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Error: $e';
      });
    }
  }

  Future<void> _startDiscovery() async {
    try {
      setState(() {
        _statusMessage = 'Starting discovery...';
        _readers = [];
      });

      final result = await StripeTerminalService.discoverReaders();

      setState(() {
        _statusMessage = 'Discovery started: $result';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Error: $e';
      });
    }
  }

  Future<void> _connectToReader(String readerId) async {
    try {
      setState(() {
        _statusMessage = 'Connecting to reader...';
      });

      final result = await StripeTerminalService.connectToReader(readerId);

      setState(() {
        _statusMessage = 'Connected: $result';
      });
    } catch (e) {
      setState(() {
        _statusMessage = 'Error: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Stripe Terminal'),
      ),
      body: Column(
        children: [
          Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'Status: $_statusMessage',
              style: TextStyle(fontSize: 16),
            ),
          ),
          ElevatedButton(
            onPressed: _startDiscovery,
            child: Text('Discover Readers'),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: _readers.length,
              itemBuilder: (context, index) {
                final reader = _readers[index];
                return ListTile(
                  title: Text(reader['serialNumber'] ?? 'Unknown Reader'),
                  subtitle: Text('${reader['deviceType']} - Battery: ${reader['batteryLevel']}%'),
                  onTap: () => _connectToReader(reader['id']),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}