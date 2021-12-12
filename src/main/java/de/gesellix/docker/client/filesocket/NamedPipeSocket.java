package de.gesellix.docker.client.filesocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.file.FileSystemException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class NamedPipeSocket extends FileSocket {

  private static final Logger log = LoggerFactory.getLogger(NamedPipeSocket.class);

  private AsynchronousFileByteChannel channel;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private InputStream inputStream;
  private OutputStream outputStream;

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    if (!(endpoint instanceof InetSocketAddress)) {
      throw new IllegalArgumentException("Expected endpoint to be a InetSocketAddress");
    }

    InetSocketAddress inetSocketAddress = (InetSocketAddress) endpoint;
    InetAddress address = inetSocketAddress.getAddress();
    String socketPath = decodeHostname(address);
    log.debug("connect via '{}'...", socketPath);

    socketPath = socketPath.replace("/", "\\\\");

    long startedAt = System.currentTimeMillis();
    timeout = Math.max(timeout, 10_000);
    while (true) {
      try {
        channel = new AsynchronousFileByteChannel(
            AsynchronousFileChannel.open(
                Paths.get(socketPath),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            )
        );
        break;
      }
      catch (FileSystemException e) {
        if (System.currentTimeMillis() - startedAt >= timeout) {
          throw new RuntimeException(e);
        }
        else {
          // requires a bit more code and the net.java.dev.jna:jna dependency
//          Kernel32.INSTANCE.WaitNamedPipe(socketFileName, 100);
          try {
            Thread.sleep(100);
          }
          catch (InterruptedException ignored) {
          }
        }
      }
    }
  }

  @Override
  public InputStream getInputStream() {
    if (inputStream == null) {
      this.inputStream = Channels.newInputStream(channel);
    }
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    if (outputStream == null) {
      this.outputStream = Channels.newOutputStream(channel);
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
    if (channel != null) {
      channel.close();
    }
    if (inputStream != null) {
      inputStream.close();
    }
    if (outputStream != null) {
      outputStream.close();
    }
  }
}
