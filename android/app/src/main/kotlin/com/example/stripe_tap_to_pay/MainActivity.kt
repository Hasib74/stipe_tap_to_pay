package com.example.stripe_tap_to_pay

// In your MainActivity.kt file

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.TerminalApplicationDelegate
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.log.LogLevel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import java.io.IOException


class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.your_app_name/stripe_terminal"
    private val REQUEST_CODE_LOCATION = 1

    // WARNING: This is for testing only! Never include your secret key in client code
    private val STRIPE_SECRET_KEY = "sk_test_your_test_key_here"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]) { call, result ->
            when (call.method) {
                "initializeTerminal" -> {
                    requestLocationPermission(result)
                }

                "discoverReaders" -> {
                    val discoveryMethod = call.argument<String>("discoveryMethod") ?: "bluetooth"
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                    }
                    discoverReaders(discoveryMethod, result)
                }

                "connectToReader" -> {
                    val readerId = call.argument<String>("readerId")
                    if (readerId != null) {
                        connectToReader(readerId, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Reader ID is required", null)
                    }
                }

                "createPaymentIntent" -> {
                    val amount = call.argument<Int>("amount") ?: 0
                    val currency = call.argument<String>("currency") ?: "usd"
                    createPaymentIntent(amount, currency, result)
                }

                "collectPayment" -> {
                    val paymentIntentId = call.argument<String>("paymentIntentId")
                    if (paymentIntentId != null) {
                        collectPayment(paymentIntentId, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Payment Intent ID is required", null)
                    }
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun requestLocationPermission(result: MethodChannel.Result) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION
            )
            // This is async, so we'll return pending for now
            result.success("PERMISSION_REQUESTED")
        } else {
            initializeTerminal(result)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_LOCATION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission granted, but we need to be called again from Flutter
            // to initialize Terminal
        } else {
            Log.e("StripeTerminal", "Location permission denied")
        }
    }

    private fun initializeTerminal(result: MethodChannel.Result) {
        val listener = object : TerminalListener {
            override fun onConnectionStatusChange(status: ConnectionStatus) {
                Log.d("StripeTerminal", "Connection status changed: $status")
                // You could send this status back to Flutter with another method channel call
            }

            override fun onPaymentStatusChange(status: PaymentStatus) {
                Log.d("StripeTerminal", "Payment status changed: $status")
                // You could send this status back to Flutter with another method channel call
            }
        }

        val logLevel = LogLevel.VERBOSE

        val tokenProvider = object : ConnectionTokenProvider {
            override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
                createConnectionTokenDirectly(callback)
            }
        }

        try {
            if (!Terminal.isInitialized()) {
                Terminal.initTerminal(applicationContext, logLevel, tokenProvider, listener)
                Log.d("StripeTerminal", "Terminal SDK initialized successfully")
                result.success("INITIALIZED")
            } else {
                result.success("ALREADY_INITIALIZED")
            }
        } catch (e: Exception) {
            result.error("INITIALIZATION_ERROR", e.message, null)
        }
    }

    private fun createConnectionTokenDirectly(callback: ConnectionTokenCallback) {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api.stripe.com/v1/terminal/connection_tokens")
            .addHeader("Authorization", "Bearer $STRIPE_SECRET_KEY")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure(
                    ConnectionTokenException("Failed to fetch connection token", e)
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onFailure(
                        ConnectionTokenException(
                            "Connection token request failed with code: ${response.code}"
                        )
                    )
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody)
                    val secret = jsonObject.getString("secret")
                    callback.onSuccess(secret)
                } catch (e: Exception) {
                    callback.onFailure(
                        ConnectionTokenException("Invalid JSON response", e)
                    )
                }
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun discoverReaders(discoveryMethod: String, result: MethodChannel.Result) {
        if (!Terminal.isInitialized()) {
            result.error("NOT_INITIALIZED", "Terminal SDK is not initialized", null)
            return
        }

        try {
         /*   val method = when (discoveryMethod) {
                "bluetooth" -> ConnectionConfiguration.BluetoothConnectionConfiguration(
                    locationId = "{{LOCATION_ID}}", // Replace with actual location ID
                    autoReconnectOnUnexpectedDisconnect = true,
                    bluetoothReaderListener = TODO(),
                    // Add your reader listener here
                )

                "internet" -> DiscoveryMethod.INTERNET
                else -> DiscoveryMethod.BLUETOOTH_SCAN
            }

            val config = DiscoveryConfiguration(method, 60000)*/


            val config = when (discoveryMethod) {
                "bluetooth" -> {
                    val config = DiscoveryConfiguration.BluetoothDiscoveryConfiguration(
                        timeout = 60000
                    )
                    config
                }
                "internet" -> {
                    val config = DiscoveryConfiguration.InternetDiscoveryConfiguration(
                        timeout = 60000
                    )
                    config
                }
                "usb" -> {
                    val config = DiscoveryConfiguration.UsbDiscoveryConfiguration(
                        timeout = 60000
                    )
                    config
                }
//                "taptopay" -> {
//                    if (Terminal.getInstance().isApplePaySupported) {
//                        val config = DiscoveryConfiguration.TapToPayDiscoveryConfiguration()
//                        config
//                    } else {
//                        result.error("TAP_TO_PAY_NOT_SUPPORTED", "Tap to Pay is not supported on this device", null)
//                        return
//                    }
//                }
                else -> {
                    val config = DiscoveryConfiguration.BluetoothDiscoveryConfiguration(
                        timeout = 60000
                    )
                    config
                }
            }


            Terminal.getInstance().discoverReaders(config, object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    // Convert readers to a format that can be sent over the method channel
                    val readersList = readers.map { reader ->
                        mapOf(
                            "id" to reader.id,
                            "deviceType" to reader.deviceType.toString(),
                            "serialNumber" to reader.serialNumber,
                            "batteryLevel" to reader.batteryLevel
                        )
                    }

                    // Send the readers to Flutter
                    runOnUiThread {
                        flutterEngine?.dartExecutor?.binaryMessenger?.let {
                            MethodChannel(it, CHANNEL)
                                .invokeMethod("onReadersDiscovered", readersList)
                        }
                    }
                }
            }, object : com.stripe.stripeterminal.external.callable.Callback {
                override fun onFailure(e: TerminalException) {
                    TODO("Not yet implemented")

                    result.error("DISCOVERY_ERROR", e.message, null)

                }
//                override fun onSuccess() {
//                    result.success("DISCOVERY_STARTED")
//                }
//
//                override fun onFailure(e: TerminalException) {
//                    result.error("DISCOVERY_ERROR", e.message, null)
//                }

                //                override fun onFailure(call: Call, e: okio.IOException) {
//                    TODO("Not yet implemented")
//
//                    result.success("DISCOVERY_STARTED")
//
//                }
//
//                override fun onResponse(call: Call, response: Response) {
//                    result.error("DISCOVERY_ERROR", e.message, null)
//                }
                override fun onSuccess() {
                    TODO("Not yet implemented")


                                      result.success("DISCOVERY_STARTED")

                }
            })
        } catch (e: Exception) {
            result.error("DISCOVERY_ERROR", e.message, null)
        }
    }

    private fun connectToReader(readerId: String, result: MethodChannel.Result) {
        // Implementation for connecting to a reader
        // This would involve finding the reader in the list of discovered readers
        // and connecting to it
    }

    private fun createPaymentIntent(amount: Int, currency: String, result: MethodChannel.Result) {
        // Implementation for creating a payment intent
    }

    private fun collectPayment(paymentIntentId: String, result: MethodChannel.Result) {
        // Implementation for collecting a payment
    }
}