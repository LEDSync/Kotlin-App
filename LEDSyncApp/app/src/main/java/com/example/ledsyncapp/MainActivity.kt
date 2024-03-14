package com.example.ledsyncapp

import com.example.ledsyncapp.R
import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


open class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val udpThread = Thread(UdpReceiverRunner(::callbackFnc))
        udpThread.start()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            v.findViewById<Button>(R.id.test_button).setOnClickListener {
                val foundDevicesScrollView: ConstraintLayout = findViewById(R.id.FoundDevicesScrollView)
                var deviceView: NewFoundDeviceView = NewFoundDeviceView(baseContext)
                foundDevicesScrollView.addView(deviceView)
                sendUDP()
            }
            insets
        }
    }

    private fun showToast(message: String) {
        val myToast = Toast.makeText(baseContext,message, Toast.LENGTH_SHORT)
        myToast.show()
    }

    private fun sendUDP() {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            val sendData = "DEVICEID".toByteArray()
            var broadcastAddress: InetAddress? = getBroadcastAddress()
            broadcastAddress ?: return showToast("Broadcast Address was not found!")
            broadcastAddress = InetAddress.getByName("145.93.49.171")

            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, 9080)
            showToast("Sending UDP Package to ${sendPacket.address}")
            socket.send(sendPacket)
        } catch (e: IOException) {
            showToast(e.toString())
        }
    }

    private fun callbackFnc(message: String, origin: InetAddress) {
        val elements = message.split(':')
        if (elements.size < 2)
            return

        if (elements[0] != "DEVICENAME")
            return

        val deviceName: String = elements[1]
        this@MainActivity.runOnUiThread {
            showToast(origin.hostName + ": " + deviceName)
        }
    }

    private fun getBroadcastAddress(): InetAddress? {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val wlanInterface: NetworkInterface? = networkInterfaces.find { x -> x.name.contains("wlan") }
        wlanInterface?: return null
        val ip4connection: InterfaceAddress? = wlanInterface.interfaceAddresses.toList().find { x -> x.address.hostAddress?.matches(Regex("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|\$)){4}"))
            ?: return null }
        return ip4connection?.broadcast ?: return null
    }
}

class UdpReceiverRunner(_messageReceivedCallback: (message: String, origin: InetAddress) -> Unit) : Runnable {
    private val messageReceivedCallback: ((message: String, origin: InetAddress) -> Unit) = _messageReceivedCallback

    override fun run() {
        val buffer = ByteArray(2048)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(9080, InetAddress.getByName("0.0.0.0"))
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

class NewFoundDeviceView : View {
    private val deviceName: String
    private val deviceIp: String

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.NewFoundDeviceView,
            0, 0
        ).apply {
            try {
                deviceName = getString(R.styleable.NewFoundDeviceView_deviceName) ?: "Unnamed Device"
                deviceIp = getString(R.styleable.NewFoundDeviceView_deviceIp) ?: "Unkown IP"

                initiate();
            } finally {
                recycle()
            }

        }
    }

    constructor(context: Context) : super(context)
    {
        deviceName = "Test1"
        deviceIp = "Test2"
        initiate()
    }

    fun initiate() {
        findViewById<TextView>(R.id.found_device_ip).text = deviceIp
        findViewById<TextView>(R.id.found_device_name).text = deviceName
    }

    fun getName(): String { return deviceName }
    fun getIP(): String { return deviceIp }
}




// imports have been left out
class FoundDeviceView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val deviceName: TextView
    private val deviceIP: TextView

    init {
        /*orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL*/
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.found_device, this, true)
        deviceName = getChildAt(0) as TextView
        deviceIP = getChildAt(1) as TextView
    }
}