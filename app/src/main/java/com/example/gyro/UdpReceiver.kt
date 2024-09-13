package com.example.gyro

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class ResponseCodeWrapper(
    var value: Int = 0,
    var message: String = ""
)

object UdpReceiver {

    val ACK = 0xFF
    val NACK = 0xFE

    private var socketReceiveAck : DatagramSocket? = null

    fun receiveHeaderAcknowledgement(port: Int, frameId: Int, responseCode: ResponseCodeWrapper): Boolean {
        val executor = Executors.newSingleThreadExecutor()
        val readResponse = Callable {
            try {
                val buffer = ByteArray(64)
                val packet = DatagramPacket(buffer, buffer.size)

                if (socketReceiveAck == null) {
                    socketReceiveAck = DatagramSocket(port)
                }
                socketReceiveAck?.soTimeout = 5000
                socketReceiveAck?.receive(packet)

                // Manage reception
                responseCode.value = buffer[0].toInt()
                // if (response.toInt() == NACK || response.toInt() != ACK) {
                //     return@Callable false
                // }
                return@Callable responseCode.value == 255

                /*
                val frameIdReceived = (buffer[1].toInt() and 0xFF shl 8) or (buffer[2].toInt() and 0xFF)
                // Finally, check if the received frame ID matches the expected frame ID
                return@Callable frameIdReceived != frameId
                 */

                // return@Callable true

            } catch (e: IOException) {
                e.printStackTrace()
                responseCode.message = e.message.toString()
                return@Callable false // Or handle the exception appropriately
            }
        }
        val future = executor.submit(readResponse)
        executor.shutdown()
        return future.get() // This will block until the result is available
    }

    fun receiveUdpAcknowledgement(port: Int, frameId: Int, rowId: Int, bufferSize: Int): Boolean {
        require(bufferSize >= 5) { "Input array must have exactly 4 bytes bytes" }

        // Waiting for response from pear
        // ACK = 0xFF and NACK = 0xFE
        // FF 00 00 00 00
        // 1st byte = ACK or NACK
        // 2nd to 3rd bytes = frameId
        // 4th to 5th bytes = rowId
        val executor = Executors.newSingleThreadExecutor()
        val readResponse = Callable {
            try {
                DatagramSocket(port).use {
                    val buffer = ByteArray(bufferSize)
                    val packet = DatagramPacket(buffer, buffer.size)

                    if (socketReceiveAck == null) {
                        socketReceiveAck = DatagramSocket(port)
                    }
                    socketReceiveAck?.receive(packet)

                    // Manage reception
                    val response = buffer[0]
                    if (response.toInt() == NACK || response.toInt() != ACK) {
                        return@Callable false
                    }
                    /*
                    val frameIdReceived = (buffer[1].toInt() and 0xFF shl 8) or (buffer[2].toInt() and 0xFF)
                    if (frameIdReceived != frameId) {
                        return@Callable false
                    }

                    val rowIdReceived = (buffer[3].toInt() and 0xFF shl 8) or (buffer[4].toInt() and 0xFF)
                    // Finally, check if the received row ID matches the expected row ID
                    return@Callable rowIdReceived == rowId
                    */
                    return@Callable true
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return@Callable false // Or handle the exception appropriately
            }
        }
        val future = executor.submit(readResponse)
        executor.shutdown()
        return future.get() // This will block until the result is available
    }

    fun close() {
        socketReceiveAck?.close()
        socketReceiveAck = null
    }
}