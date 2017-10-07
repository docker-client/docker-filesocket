package de.gesellix.docker.client.filesocket

import java.net.Socket

class NpipeSocketFactory : FileSocketFactory() {

    @Override
    override fun createSocket(): Socket = NamedPipeSocket()
}
