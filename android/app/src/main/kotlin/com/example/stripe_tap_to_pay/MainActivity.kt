package com.example.stripe_tap_to_pay

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
//import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.example.your_app_name/stripe_terminal"
    private val REQUEST_CODE_LOCATION = 1

    // WARNING: This is for testing only! Never include your secret key in client code
    private val STRIPE_SECRET_KEY = "sk_test_your_test_key_here"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must initialize TerminalApplicationDelegate for SDK lifecycle
        TerminalApplicationDelegate.onCreate(this.application)
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) { call, result ->
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


                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    // -------------------------
    // Permission & Initialization
    // -------------------------
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
            result.success("PERMISSION_REQUESTED")
        } else {
            initializeTerminal(result)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("StripeTerminal", "Location permission granted. Initializing Terminal...")
            initializeTerminal(object : MethodChannel.Result {
                override fun success(result: Any?) {
                    Log.d("StripeTerminal", "Terminal initialized after permission")
                }

                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    Log.e("StripeTerminal", "Error initializing Terminal: $errorMessage")
                }

                override fun notImplemented() {}
            })
        } else {
            Log.e("StripeTerminal", "Location permission denied")
        }
    }

    private fun initializeTerminal(result: MethodChannel.Result) {
        val listener = object : TerminalListener {
            override fun onConnectionStatusChange(status: ConnectionStatus) {
                Log.d("StripeTerminal", "Connection status changed: $status")
            }

            override fun onPaymentStatusChange(status: PaymentStatus) {
                Log.d("StripeTerminal", "Payment status changed: $status")
            }
        }

        val tokenProvider = object : ConnectionTokenProvider {
            override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
                createConnectionTokenDirectly(callback)
            }
        }

        try {
            if (!Terminal.isInitialized()) {
                Terminal.initTerminal(applicationContext, LogLevel.VERBOSE, tokenProvider, listener)
                Log.d("StripeTerminal", "Terminal SDK initialized successfully")
                result.success("INITIALIZED")
            } else {
                Log.d("StripeTerminal", "Terminal SDK already initialized")
                result.success("ALREADY_INITIALIZED")
            }
        } catch (e: Exception) {
            Log.e("StripeTerminal", "Initialization error: ${e.message}")
            result.error("INITIALIZATION_ERROR", e.message, null)
        }
    }

    // -------------------------
    // Connection Token Fetch
    // -------------------------
    private fun createConnectionTokenDirectly(callback: ConnectionTokenCallback) {
        val client = okhttp3.OkHttpClient()

        val request = okhttp3.Request.Builder()
            .url("https://api.stripe.com/v1/terminal/connection_tokens")
            .addHeader("Authorization", "Bearer $STRIPE_SECRET_KEY")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        client.newCall(request).enqueue(object :okhttp3.Callback{
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback.onFailure(ConnectionTokenException("Failed to fetch connection token", e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    callback.onFailure(ConnectionTokenException("Connection token request failed with code: ${response.code}"))
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody)
                    val secret = jsonObject.getString("secret")
                    callback.onSuccess(secret)
                } catch (e: Exception) {
                    callback.onFailure(ConnectionTokenException("Invalid JSON response", e))
                }
            }


        });
    }

    // -------------------------
    // Discover Readers
    // -------------------------
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION])
    private fun discoverReaders(discoveryMethod: String, result: MethodChannel.Result) {
        if (!Terminal.isInitialized()) {
            Log.e("StripeTerminal", "Terminal SDK is not initialized")
            result.error("NOT_INITIALIZED", "Terminal SDK is not initialized", null)
            return
        }

        val config = when (discoveryMethod) {
            "bluetooth" -> DiscoveryConfiguration.BluetoothDiscoveryConfiguration(timeout = 60000)
            "internet" -> DiscoveryConfiguration.InternetDiscoveryConfiguration(timeout = 60000)
            "usb" -> DiscoveryConfiguration.UsbDiscoveryConfiguration(timeout = 60000)
            else -> DiscoveryConfiguration.BluetoothDiscoveryConfiguration(timeout = 60000)
        }

        Terminal.getInstance().discoverReaders(config, object : DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                val readersList = readers.map { reader ->
                    mapOf(
                        "id" to reader.id,
                        "deviceType" to reader.deviceType.toString(),
                        "serialNumber" to reader.serialNumber,
                        "batteryLevel" to reader.batteryLevel
                    )
                }

                runOnUiThread {
                    flutterEngine?.dartExecutor?.binaryMessenger?.let {
                        MethodChannel(it, CHANNEL).invokeMethod("onReadersDiscovered", readersList)
                    }
                }
            }
        }, object : com.stripe.stripeterminal.external.callable.Callback {
            override fun onSuccess() {
                Log.d("StripeTerminal", "Reader discovery started")
                result.success("DISCOVERY_STARTED")
            }

            override fun onFailure(e: TerminalException) {
                Log.e("StripeTerminal", "Discovery error: ${e.message}")
                result.error("DISCOVERY_ERROR", e.message, null)
            }
        })
    }
}
