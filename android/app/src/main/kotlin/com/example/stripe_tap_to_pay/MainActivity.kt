package com.example.stripe_tap_to_pay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.log.LogLevel
import okhttp3.Call
import okhttp3.Callback
import  okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.RequestBody

import org.json.JSONObject
import java.io.IOException

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.example/stripe_terminal"
    private lateinit var tokenProvider: ConnectionTokenProvider
    private var discoveryCancelable: Cancelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        flutterEngine?.dartExecutor?.binaryMessenger?.let {
            MethodChannel(it, CHANNEL)
                .setMethodCallHandler { call, result ->
                    if (call.method == "startTapToPay") {
                        initStripeTerminal(result)
                    } else {
                        result.notImplemented()
                    }
                }
        }
    }

    private fun initStripeTerminal(result: MethodChannel.Result) {
        // Token provider to fetch connection token from your backend


        Log.d("StripeTerminal", "Api calling ....")


        print("Api calling ... ")

        tokenProvider = object : ConnectionTokenProvider {
            override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://your-server.com/connection_token") // change this
                    .post(RequestBody.create(null, ByteArray(0)))
                    .build()



                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                       // callback.onFailure(e)

                        Log.d("StripeTerminal", "Api calling .... error ${e}")


                        print("Api calling ...  error ${e} ")

                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { // response leak এড়াতে
                            val json = JSONObject(response.body?.string() ?: "")
                            val secret = json.getString("secret")
                            callback.onSuccess(secret)
                        }
                    }
                })







            }
        }

        // Safe initTerminal if not already initialized
        if (!Terminal.isInitialized()) {
            Terminal.initTerminal(
                applicationContext,
                LogLevel.VERBOSE,
                tokenProvider,
                object : TerminalListener {
//                    override fun onUnexpectedReaderDisconnect(reader: Reader) {
//                        Log.d("StripeTerminal", "Reader disconnected: ${reader.serialNumber}")
//                    }

                    override fun onConnectionStatusChange(status: ConnectionStatus) {
                        Log.d("StripeTerminal", "Connection status: $status")
                    }

                    override fun onPaymentStatusChange(status: PaymentStatus) {
                        Log.d("StripeTerminal", "Payment status: $status")
                    }
                }
            )
        }

        startDiscovery(result)
    }

    private fun startDiscovery(result: MethodChannel.Result) {
        val terminal = Terminal.getInstance()
        val config = DiscoveryConfiguration.BluetoothDiscoveryConfiguration(

            isSimulated = false,
        )



        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        // Ensure you have the terminal instance and result object defined before this.

        discoveryCancelable = terminal.discoverReaders(
            DiscoveryConfiguration.BluetoothDiscoveryConfiguration(
                isSimulated = false // Set to true for development/testing
            ),
            object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    if (readers.isNotEmpty()) {
                        val reader = readers.first()

                        // ✅ The fix: provide the locationId to BluetoothConnectionConfiguration
                        // You must replace "YOUR_LOCATION_ID" with the actual ID from your Stripe dashboard.
                        // This ID is associated with the physical location where the reader is being used.
                        // You can get this from your backend server or pass it from Flutter.
                        val locationId = "YOUR_LOCATION_ID"

                        terminal.connectReader(
                            reader,
                            ConnectionConfiguration.BluetoothConnectionConfiguration(
                                locationId,
                                autoReconnectOnUnexpectedDisconnect = TODO(),
                                bluetoothReaderListener = TODO()
                            ),
                            object : ReaderCallback {
                                override fun onSuccess(connectedReader: Reader) {
                                    result.success("Connected to: ${connectedReader.serialNumber}")
                                }

                                override fun onFailure(e: TerminalException) {
                                    result.error("ConnectionError", e.message, null)
                                }
                            }
                        )
                    } else {
                        // To avoid a scenario where the UI freezes, you might want to send a success
                        // with a message, or simply let the discovery continue and wait for a reader.
                        // Here, an error is fine if you expect a reader to be available immediately.
                        result.error("NoReaders", "No Bluetooth readers found", null)
                    }
                }
            },
            object : com.stripe.stripeterminal.external.callable.Callback {
                override fun onSuccess() {
                    Log.d("StripeTerminal", "Discovery started successfully.")
                    // You might want to update the UI here to indicate that discovery is in progress.
                }
                override fun onFailure(e: TerminalException) {
                    result.error("DiscoveryFailed", e.message, null)
                }
            }
        )

    }
}