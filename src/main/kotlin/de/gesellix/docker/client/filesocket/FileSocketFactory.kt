package de.gesellix.docker.client.filesocket

import okhttp3.Dns
import java.net.InetAddress
import java.net.Socket

import javax.net.SocketFactory

abstract class FileSocketFactory : SocketFactory(), Dns {

    @Override
    override fun lookup(hostname: String): List<InetAddress> {
        return if (hostname.endsWith(FileSocket.SOCKET_MARKER))
            listOf(InetAddress.getByAddress(hostname, kotlin.ByteArray(4)))
        else Dns.SYSTEM.lookup(hostname)
    }

    @Override
    override fun createSocket(s: String, i: Int): Socket {
        throw  UnsupportedOperationException()
    }

    @Override
    override fun createSocket(s: String, i: Int, inetAddress: InetAddress, i1: Int): Socket {
        throw UnsupportedOperationException()
    }

    @Override
    override fun createSocket(inetAddress: InetAddress, i: Int): Socket {
        throw UnsupportedOperationException()
    }

    @Override
    override fun createSocket(inetAddress: InetAddress, i: Int, inetAddress1: InetAddress, i1: Int): Socket {
        throw UnsupportedOperationException()
    }
}
