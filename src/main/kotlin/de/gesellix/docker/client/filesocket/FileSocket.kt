package de.gesellix.docker.client.filesocket

import java.net.InetAddress
import java.net.Socket

abstract class FileSocket : Socket() {

    companion object {
        const val SOCKET_MARKER: String = ".socket"
    }

    fun encodeHostname(hostname: String): String {
        return "${HostnameEncoder().encode(hostname)}${SOCKET_MARKER}"
    }

    fun decodeHostname(address: InetAddress): String {
        val hostName = address.hostName
        return HostnameEncoder().decode(hostName.substring(0, hostName.indexOf(SOCKET_MARKER)))
    }
}
