package de.gesellix.docker.client.filesocket

import mu.KotlinLogging
import org.newsclub.net.unix.AFUNIXSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.SocketAddress

private val log = KotlinLogging.logger {}

class UnixSocket : FileSocket() {

    var socket: AFUNIXSocket? = null

    @Override
    override fun connect(endpoint: SocketAddress, timeout: Int) {
        if (endpoint !is InetSocketAddress) {
            throw IllegalArgumentException("expected endpoint to be a InetSocketAddress")
        }

        val address = endpoint.address
        val socketPath = decodeHostname(address)

        log.debug("connect via '$socketPath'...")
        val socketFile = File(socketPath)

        socket = AFUNIXSocket.newInstance()

        var socketTimeout = timeout
        if (timeout < 0) {
            socketTimeout = 0
        }
        socket?.connect(AFUNIXSocketAddress(socketFile), socketTimeout)
        socket?.soTimeout = socketTimeout
    }

    @Override
    override fun getInputStream(): InputStream? {
        return socket?.inputStream
    }

    @Override
    override fun getOutputStream(): OutputStream? {
        return socket?.outputStream
    }

    @Override
    override fun bind(bindpoint: SocketAddress) {
        socket?.bind(bindpoint)
    }

    @Override
    override fun isConnected(): Boolean {
        return if (socket != null) socket!!.isConnected else false
    }

    @Override
    override fun close() {
        synchronized(this) {
            socket?.close()
        }
    }
}
