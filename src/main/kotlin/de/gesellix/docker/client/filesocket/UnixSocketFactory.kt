package de.gesellix.docker.client.filesocket

import java.net.Socket

class UnixSocketFactory : FileSocketFactory() {

    @Override
    override fun createSocket(): Socket = UnixSocket()
}
