package de.gesellix.docker.client.filesocket;

import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;
import static com.sun.jna.platform.win32.WinNT.FILE_FLAG_OVERLAPPED;
import static com.sun.jna.platform.win32.WinNT.GENERIC_READ;
import static com.sun.jna.platform.win32.WinNT.GENERIC_WRITE;
import static com.sun.jna.platform.win32.WinNT.OPEN_EXISTING;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class NamedPipeSocket extends FileSocket {

  private static final Logger log = LoggerFactory.getLogger(NamedPipeSocket.class);

  private WinNT.HANDLE handle;
  private boolean connected = false;
  private boolean closed = false;

  private BufferedSource source;
  private BufferedSink sink;

  private final int ioTimeoutMillis = 2000;

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

  void connect(String socketPath) throws IOException {
    socketPath = socketPath.replace("/", "\\");
    log.debug("connect via '{}'...", socketPath);

    Kernel32.INSTANCE.WaitNamedPipe(socketPath, 200);
    handle = Kernel32.INSTANCE.CreateFile(
        socketPath,
        GENERIC_READ | GENERIC_WRITE,
        0,
        null,
        OPEN_EXISTING,
        //0,
        FILE_FLAG_OVERLAPPED,
        null
    );

    if (INVALID_HANDLE_VALUE.equals(handle)) {
      int err = Kernel32.INSTANCE.GetLastError();
      throw new IOException("Failed to open Named Pipe '" + socketPath + "', WinError=" + err);
    }

    connected = true;
    source = Okio.buffer(new NamedPipeSource(handle, ioTimeoutMillis));
    sink = Okio.buffer(new NamedPipeSink(handle, ioTimeoutMillis));
  }

  @Override
  public InputStream getInputStream() throws IOException {
    ensureOpen();
    return source.inputStream();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    ensureOpen();

    // Wrap the Okio OutputStream so flush() also flushes the BufferedSink
    return new FilterOutputStream(sink.outputStream()) {
      @Override
      public void flush() throws IOException {
        super.flush();       // flush Java's wrapper
        sink.flush();        // flush Okio buffer to NamedPipeSink
      }
    };
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    try {
      if (handle != null && !INVALID_HANDLE_VALUE.equals(handle)) {
        // Cancel any pending read/write before closing to avoid CloseHandle() hang
        ExtendedKernel32.INSTANCE.CancelIoEx(handle, null);
      }

      if (source != null) {
        source.close();
      }
      if (sink != null) {
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
