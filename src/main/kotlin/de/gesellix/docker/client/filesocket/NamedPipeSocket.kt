package de.gesellix.docker.client.filesocket

import mu.KotlinLogging
import java.io.*
import java.net.InetSocketAddress
import java.net.SocketAddress

private val log = KotlinLogging.logger {}

class NamedPipeSocket : FileSocket() {

    var namedPipe: RandomAccessFile? = null
    var closed: Boolean = false

    @Override
    override fun connect(endpoint: SocketAddress, timeout: Int) {
        if (endpoint !is InetSocketAddress) {
            throw IllegalArgumentException("expected endpoint to be a InetSocketAddress")
        }

        val address = endpoint.address
        var socketPath = decodeHostname(address)
        log.debug("connect via '$socketPath'...")

        socketPath = socketPath.replace("/", "\\\\")
        this.namedPipe = RandomAccessFile(socketPath, "rw")
    }

    @Override
    override fun getInputStream(): InputStream? {
        return if (namedPipe != null) FileInputStream(namedPipe!!.getFD()) else null
    }

    @Override
    override fun getOutputStream(): OutputStream? {
        return if (namedPipe != null) FileOutputStream(namedPipe!!.getFD()) else null
    }

    @Override
    override fun isClosed(): Boolean {
        return closed
    }

    @Override
    override fun close() {
        synchronized(this) {
            if (namedPipe != null) namedPipe!!.close()
            closed = true
        }
    }
}
