package de.gesellix.docker.client.filesocket;

import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.WinNT;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Timeout;

public class NamedPipeSocket extends FileSocket {

  private static final Logger log = LoggerFactory.getLogger(NamedPipeSocket.class);

  private WinNT.HANDLE handle;
  private boolean connected = false;
  private boolean closed = false;

  private BufferedSource source;
  private BufferedSink sink;

  private final Timeout ioTimeout = new Timeout().timeout(1000, TimeUnit.MILLISECONDS);

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    if (!(endpoint instanceof InetSocketAddress)) {
      throw new IllegalArgumentException("Expected endpoint to be a InetSocketAddress");
    }

    InetSocketAddress inetSocketAddress = (InetSocketAddress) endpoint;
    InetAddress address = inetSocketAddress.getAddress();
    String socketPath = decodeHostname(address);
    connect(socketPath);
  }

  void connect(String socketPath) {
    socketPath = socketPath.replace("/", "\\");
    log.debug("connect via '{}'...", socketPath);

    handle = NamedPipeUtils.connect(socketPath, 10_000, 500, 50);

    connected = true;
    source = Okio.buffer(new NamedPipeSource(handle, ioTimeout));
    sink = Okio.buffer(new NamedPipeSink(handle, ioTimeout));
  }

  @Override
  public InputStream getInputStream() throws IOException {
    ensureOpen();
    return source.inputStream();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    ensureOpen();
    return sink.outputStream();
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    log.debug("closing handle {}...", handle);
    try {
      if (handle != null && !INVALID_HANDLE_VALUE.equals(handle)) {
        // Cancel any pending read/write before closing to avoid CloseHandle() hang
        ExtendedKernel32.INSTANCE.CancelIoEx(handle, null);
      }

      if (source != null) {
        log.debug("closing source {}...", source);
        source.close();
      }
      if (sink != null) {
        log.debug("closing sink {}...", sink);
        sink.close();
      }
    } finally {
      if (handle != null) {
        NamedPipeUtils.closeHandle(handle);
      }
      closed = true;
      connected = false;
    }
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  private void ensureOpen() throws IOException {
    if (closed) {
      throw new IOException("NamedPipeSocket is closed");
    }
    if (!connected) {
      throw new IOException("NamedPipeSocket is not connected");
    }
  }
}
