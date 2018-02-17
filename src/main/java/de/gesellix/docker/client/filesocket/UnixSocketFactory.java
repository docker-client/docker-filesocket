package de.gesellix.docker.client.filesocket;

import java.net.Socket;

class UnixSocketFactory extends FileSocketFactory {

    @Override
    public Socket createSocket() {
        return new UnixSocket();
    }
}
