package de.gesellix.docker.client.filesocket;

import okhttp3.Dns;

import javax.net.SocketFactory;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

abstract class FileSocketFactory extends SocketFactory implements Dns {

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (hostname.endsWith(FileSocket.SOCKET_MARKER)) {
            return Collections.singletonList(InetAddress.getByAddress(hostname, new byte[] {0, 0, 0, 0}));
        }
        else {
            return Dns.SYSTEM.lookup(hostname);
        }
    }

    @Override
    public Socket createSocket(String s, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) {
        throw new UnsupportedOperationException();
    }
}
