package de.gesellix.docker.client.filesocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

class NamedPipeDelegatingInputStream extends InputStream {

  private final RandomAccessFile namedPipe;

  public NamedPipeDelegatingInputStream(RandomAccessFile namedPipe) {
    this.namedPipe = namedPipe;
  }

  @Override
  public int read() throws IOException {
    return namedPipe.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return namedPipe.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return namedPipe.read(b, off, len);
  }

  @Override
  public void close() throws IOException {
    namedPipe.close();
    super.close();
  }
}
