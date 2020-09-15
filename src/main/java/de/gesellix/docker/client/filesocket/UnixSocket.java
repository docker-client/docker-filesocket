package de.gesellix.docker.client.filesocket;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

public class UnixSocket extends FileSocket {

    private static final Logger log = LoggerFactory.getLogger(UnixSocket.class);

    private AFUNIXSocket socket = null;

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (!InetSocketAddress.class.isInstance(endpoint)) {
            throw new IllegalArgumentException("Expected endpoint to be a InetSocketAddress");
        }

        InetSocketAddress inetSocketAddress = (InetSocketAddress) endpoint;
        InetAddress address = inetSocketAddress.getAddress();
        String socketPath = decodeHostname(address);
        log.debug("connect via '{}'...", socketPath);

        File socketFile = new File(socketPath);

        socket = AFUNIXSocket.newInstance();

        int socketTimeout = timeout;
        if (timeout < 0) {
            socketTimeout = 0;
        }
        socket.connect(new AFUNIXSocketAddress(socketFile), socketTimeout);
        socket.setSoTimeout(socketTimeout);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (socket == null) {
            throw new SocketException("Socket is not initialized");
        }
        return socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (socket == null) {
            throw new SocketException("Socket is not initialized");
        }
        return socket.getOutputStream();
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        if (socket == null) {
            throw new SocketException("Socket is not initialized");
        }
        socket.bind(bindpoint);
    }

    @Override
    public boolean isConnected() {
        return socket != null &&  socket.isConnected();
    }

    @Override
    public boolean isClosed() {
        return socket != null && socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            synchronized (this) {
                socket.close();
            }
        }
    }
}
