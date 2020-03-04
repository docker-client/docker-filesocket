package de.gesellix.docker.client.filesocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NamedPipeSocket extends FileSocket {

    private static final Logger log = LoggerFactory.getLogger(NamedPipeSocket.class);

    private RandomAccessFile namedPipe = null;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private InputStream inputStream;
    private OutputStream outputStream;

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (!InetSocketAddress.class.isInstance(endpoint)) {
            throw new IllegalArgumentException("Expected endpoint to be a InetSocketAddress");
        }

        InetSocketAddress inetSocketAddress = (InetSocketAddress) endpoint;
        InetAddress address = inetSocketAddress.getAddress();
        String socketPath = decodeHostname(address);
        log.debug("connect via '{}'...", socketPath);

        socketPath = socketPath.replace("/", "\\\\");
        this.namedPipe = new RandomAccessFile(socketPath, "rw");
        this.inputStream = new NamedPipeDelegatingInputStream(namedPipe);
        this.outputStream = new NamedPipeDelegatingOutputStream(namedPipe);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (inputStream == null) {
            throw new SocketException("Socket is not initialized. Please call #connect.");
        }
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            throw new SocketException("Socket is not initialized. Please call #connect.");
        }
        return outputStream;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            // if compareAndSet() returns false closed was already true
            return;
        }
        if (namedPipe != null) {
            namedPipe.close();
        }
        if (inputStream != null) {
            inputStream.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
    }
}
