package com.example.ledsyncapp


import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets


open class MainActivity : AppCompatActivity() {
    private var arrayOfDevices: ArrayList<LEDSyncDevice?> = ArrayList<LEDSyncDevice?>()
    var adapter: LEDSyncDevice.Adapter? = null
    var deviceFinder: DeviceFinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        deviceFinder = DeviceFinder(this)

        /* UDP Thread Runner */
        val udpThread = Thread(DeviceFinder.UdpReceiverRunner(::onReceiveUDP))
        udpThread.start()


        /* Setup Found Devices */
        adapter = LEDSyncDevice.Adapter(baseContext, arrayOfDevices)
        val foundDevicesListView: ListView = findViewById(R.id.FoundDeviceListView)
        foundDevicesListView.setAdapter(adapter)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            v.findViewById<Button>(R.id.test_button).setOnClickListener {
                ClearFoundDevices()
                deviceFinder?.sendDeviceIdentificationBroadcast(this)
            }
            insets
        }
    }

    /* Found Devices */
    public fun ClearFoundDevices() {
        this@MainActivity.runOnUiThread{
            adapter?.clear()
        }
    }
    public fun AddFoundDevice(devices: ArrayList<LEDSyncDevice>) {
        this@MainActivity.runOnUiThread{
            for (device in devices) {
                adapter?.add(device)
            }
        }
    }

    public fun AddFoundDevice(device: LEDSyncDevice) {
        this@MainActivity.runOnUiThread{
            adapter?.add(device)
        }
    }

    public fun onReceiveUDP(message: String, origin: InetAddress) {
        showToast(message = message)
        val elements = message.split(':')
        if (elements.size < 2) return
        if (elements[0] != "DEVICENAME") return

        AddFoundDevice(LEDSyncDevice(baseContext, elements[1], origin.hostName))
    }

    /* Debugging Methods */
    public fun showToast(message: String) {
        this@MainActivity.runOnUiThread {
            val myToast = Toast.makeText(baseContext, message, Toast.LENGTH_SHORT)
            myToast.show()
        }
    }
}

class DeviceFinder (private val originContext: MainActivity) {
    public fun sendDeviceIdentificationBroadcast(origin: MainActivity) {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val socket = DatagramSocket()
            val sendData: String = "DEVICEID"
            var broadcastAddress: InetAddress? = getBroadcastAddress()
            broadcastAddress ?: return originContext.showToast("Broadcast Address was not found!")
            //broadcastAddress = InetAddress.getByName("145.93.49.248")

            val sendPacket = DatagramPacket(sendData.toByteArray(), sendData.toByteArray().size, broadcastAddress, 9080)
            socket.send(sendPacket)
        } catch (e: IOException) {
            originContext.showToast(e.toString())
        }
    }

    private fun getBroadcastAddress(): InetAddress? {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val wlanInterface: NetworkInterface = networkInterfaces.find { x -> x.name.contains("wlan") } ?: return null
        val ip4connection: InterfaceAddress? = wlanInterface.interfaceAddresses.toList().find { x -> x.address.hostAddress?.matches(Regex("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|\$)){4}")) ?: return null }
        return ip4connection?.broadcast ?: return null
    }

    class UdpReceiverRunner(_messageReceivedCallback: (message: String, origin: InetAddress) -> Unit) : Runnable {
        private val messageReceivedCallback: ((message: String, origin: InetAddress) -> Unit) = _messageReceivedCallback

        override fun run() {
            val buffer = ByteArray(2048)
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(9081, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true
                val packet = DatagramPacket(buffer, buffer.size)

                while(true) {
                    socket.receive(packet)
                    messageReceivedCallback.invoke(String(packet.data, StandardCharsets.UTF_8), packet.address)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket?.close()
            }
        }
    }
}

class LEDSyncDevice(context: Context, private var name: String, private val ip: String) : View(context) {
    public var view: View? = null
    private val apiURL = "http://${ip}:8080"

    fun getName(): String { return name }
    fun getIP(): String { return ip }
    fun getConfiguration(): JSONObject { return JSONObject(URL("${apiURL}/config").readText()) }
    fun changeConfiguration(name: String, value: String): Boolean {
        val url: URL = URL("${apiURL}/config/${name}?value=${value}")
        val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"

        if (connection.responseCode == 200)
        {
            reloadConfiguration()
            return true
        } else {
            return false
        }
    }

    fun reloadConfiguration() {
        val config: JSONObject = getConfiguration();
        name = config["device_name"] as String

        (view?.findViewById(R.id.found_device_name) as TextView).text = name
        (view?.findViewById(R.id.found_device_ip) as TextView).text = getIP()
    }

    fun toggleDevice(): Boolean {
        val url: URL = URL("${apiURL}/mode/toggle")
        val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        return connection.responseCode == 200
    }

    public class Adapter(context: Context, devices: ArrayList<LEDSyncDevice?>) :
        ArrayAdapter<LEDSyncDevice?>(context, 0, devices) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val device: LEDSyncDevice? = getItem(position)
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.found_device, parent, false)
            }

            device?.view = convertView!!
            device?.reloadConfiguration();

            val changeNameButton : Button = convertView.findViewById(R.id.change_name_button)
            val nameTextBox : TextView = convertView.findViewById(R.id.name_textbox)
            val offModeButton : Button = convertView.findViewById(R.id.toggle_button)
            val normalModeButton : Button = convertView.findViewById(R.id.normal_mode_button)
            val cinematicModeButton: Button = convertView.findViewById(R.id.cinematic_mode_button)

            changeNameButton.setOnClickListener { changeDeviceName(device, nameTextBox.text.toString()); nameTextBox.text = "" }
            offModeButton.setOnClickListener {  }
            normalModeButton.setOnClickListener {  }
            cinematicModeButton.setOnClickListener {  }

            return convertView
        }

        fun changeDeviceName(device: LEDSyncDevice?, newName : String) {
            if (newName.isEmpty())
                return;

            device?.changeConfiguration("device_name", newName);
        }
    }
}