package com.example.gyro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException


object UdpClient {

    private var socket: DatagramSocket? = null

    // private const val IP = "192.168.1.100" // Replace with server IP
    private const val IP = "PORT-KEN"
    private const val PORT = 5000 // Replace with server port

    fun send(data: ByteArray) {
        sendData(data)
    }

    fun close() {
        socket?.close()
        socket = null
    }

    private fun sendData(data: ByteArray) = runBlocking {
        withContext(Dispatchers.IO) {
            // Try connection
            try {
                // Create a socket
                if (socket == null) {
                    socket = DatagramSocket()
                    // socket?.setSendBufferSize(65536) // Set send buffer size to 64 KB
                }

                // Getting IP address
                val address = InetAddress.getByName(IP)

                // Creating a packet with the data and the address
                val packet = DatagramPacket(data, data.size, address, PORT)

                // Sending packet
                socket?.send(packet)

            } catch (e: SocketTimeoutException) {
                // Handle timeout exception
                println("Connection or Write timeout occurred!")
                e.printStackTrace()
                close()
            } catch (e: IOException) {
                // Handle other I/O exceptions
                println("Connection I/O error: ${e.message}")
                e.printStackTrace()
                close()
            }
        }
    }
}