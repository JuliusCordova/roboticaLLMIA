package pe.edu.upn.llmia.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import pe.edu.upn.llmia.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dos rutas a la vez, elegidas petición por petición (nunca bindProcessToNetwork):
 *  - Wi-Fi Carrito_CAM (sin Internet) para la ESP32-CAM en 192.168.4.1
 *  - red celular validada para Cloud Run por HTTPS
 */
class Links(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile var robotNetwork: Network? = null
        private set

    fun connectRobot(ssid: String, passphrase: String, onChange: (Network?, String) -> Unit) {
        disconnectRobot()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase) // solo en memoria; nunca se guarda
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (wifiCallback !== this) return
                robotNetwork = network
                onChange(network, "Robot conectado por Wi-Fi local")
            }
            override fun onLost(network: Network) {
                if (wifiCallback !== this) return
                if (robotNetwork == network) robotNetwork = null
                onChange(null, "Se perdió la conexión con el robot")
            }
            override fun onUnavailable() {
                if (wifiCallback !== this) return
                onChange(null, "No se pudo conectar: revisa la clave, la ubicación activada y el diálogo de Android")
            }
        }
        wifiCallback = callback
        cm.requestNetwork(request, callback, 30_000)
    }

    fun disconnectRobot() {
        wifiCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        wifiCallback = null
        robotNetwork = null
    }

    /** Solo CloudApi usa esta selección; RobotApi conserva robotNetwork. */
    fun cloudNetwork(): Network? {
        val cellular = cellularNetwork()
        if (cellular != null) return cellular
        if (!BuildConfig.DEBUG || !BuildConfig.ALLOW_DEFAULT_CLOUD_NETWORK) return null
        val default = cm.activeNetwork
        val caps = default?.let { cm.getNetworkCapabilities(it) }
        val internet = default?.takeIf {
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        return CloudNetworkPolicy.select(cellular, internet, robotNetwork,
            BuildConfig.DEBUG, BuildConfig.ALLOW_DEFAULT_CLOUD_NETWORK)
    }

    @Suppress("DEPRECATION")
    fun cellularNetwork(): Network? = cm.allNetworks.firstOrNull { net ->
        val caps = cm.getNetworkCapabilities(net)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class RobotOffline : IllegalStateException("Robot sin conexión Wi-Fi")
class CloudOffline : IllegalStateException("Sin red disponible para nube. Modo normal: datos móviles; modo prueba debug: Internet validado.")

internal object Http {
    fun exchange(
        network: Network,
        url: String,
        method: String = "GET",
        auth: String? = null,
        payload: ByteArray? = null,
        contentType: String? = null,
        maxResponse: Int = 128 * 1024,
        connectMs: Int = 6_000,
        readMs: Int = 25_000,
    ): Pair<Int, ByteArray> {
        val connection = network.openConnection(URL(url)) as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectMs
            connection.readTimeout = readMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json, image/jpeg, text/plain")
            if (auth != null) connection.setRequestProperty("Authorization", auth)
            if (payload != null) {
                connection.doOutput = true
                if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            if (stream == null) return status to ByteArray(0)
            val out = ByteArrayOutputStream()
            stream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxResponse) throw IllegalStateException("Respuesta HTTP demasiado grande")
                    out.write(buffer, 0, n)
                }
            }
            return status to out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}

fun Throwable.short(): String = "${javaClass.simpleName}: ${message?.take(110) ?: "sin detalle"}"

