package de.gesellix.docker.client.filesocket;

import java.net.Socket;

class NpipeSocketFactory extends FileSocketFactory {

    @Override
    public Socket createSocket() {
        return new NamedPipeSocket();
    }
}
