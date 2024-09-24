package com.example.gyro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException


object TcpClient {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    // private const val IP = "192.168.1.100" // Replace with server IP
    private const val IP = "PORT-KEN"
    private const val PORT = 5000 // Replace with server port
    private const val CONNECTION_TIMEOUT = 60000 // Connection timeout (milliseconds)
    private const val WRITE_TIMEOUT = 500 // Write timeout (milliseconds)

    fun send(data: ByteArray) {
        sendData(data)
    }

    fun close() {
        socket?.close()
        socket = null
        outputStream?.close()
        outputStream = null
    }

    private fun sendData(data: ByteArray) = runBlocking {
        withContext(Dispatchers.IO) {
            // Try connection
            try {
                // Create a socket
                if (socket == null) {
                    socket = Socket()
                    // socket?.setTcpNoDelay(true)
                    // socket?.setSendBufferSize(65536) // Set send buffer size to 64 KB
                }

                // Connect the socket if not already connected
                if (socket?.isClosed == true || socket?.isConnected == false) {
                    // Set connection timeout
                    socket?.connect(InetSocketAddress(IP, PORT), CONNECTION_TIMEOUT)
                }
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

            if (socket?.isConnected == true) {
                // Try to send data
                try {
                    // Create an output stream to send data
                    if (outputStream == null) {
                        outputStream = socket?.getOutputStream()
                    }

                    // Optionally, set the write timeout (although it's generally handled at the application layer)
                    socket?.soTimeout = WRITE_TIMEOUT

                    // Send the ByteArray data
                    outputStream?.write(data)
                    outputStream?.flush() // Make sure the data is sent immediately
                    println("Data sent: ${data.size} bytes")

                } catch (e: SocketTimeoutException) {
                    // Handle timeout exception
                    println("Connection or Write timeout occurred!")
                    e.printStackTrace()
                    close()
                } catch (e: IOException) {
                    // Handle other I/O exceptions
                    println("Sending I/O error: ${e.message}")
                    e.printStackTrace()
                    close()
                }
            }
        }
    }
}