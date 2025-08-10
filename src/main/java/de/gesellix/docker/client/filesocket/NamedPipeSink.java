package de.gesellix.docker.client.filesocket;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import okio.Buffer;
import okio.Sink;
import okio.Timeout;

public class NamedPipeSink implements Sink {

  private final WinNT.HANDLE handle;
  private final int timeoutMillis;

  public NamedPipeSink(WinNT.HANDLE handle, Timeout timeout) {
    this.handle = handle;
    this.timeoutMillis = (int) timeout.timeoutNanos() / 1000;
  }

  @Override
  public void write(@NotNull Buffer source, long byteCount) throws IOException {
    if (byteCount < 0) {
      throw new IllegalArgumentException("Invalid buffer size: " + byteCount);
    }
    if (byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Can only write max " + Integer.MAX_VALUE + " bytes");
    }

    if (byteCount == 0) {
      return;
    }

    byte[] data = source.readByteArray(byteCount);
    IntByReference bytesWritten = new IntByReference();
    //boolean ok = NamedPipeUtils.writeFromBuffer(handle, data, data.length, bytesWritten);
    boolean ok = NamedPipeUtils.writeOverlapped(handle, data, data.length, bytesWritten, timeoutMillis);
    if (!ok) {
      int err = Kernel32.INSTANCE.GetLastError();
      if (err == WinError.ERROR_OPERATION_ABORTED) {
        // Expected when CancelIoEx() is called during close()
        return;
      }
      throw new IOException("Failed to write to Named Pipe. WinError=" + err);
    }

    if (bytesWritten.getValue() <= 0) {
      throw new IOException("No bytes written to Named Pipe");
    }
  }

  @Override
  public void flush() {
    // No-op for Named Pipes
  }

  @NotNull
  @Override
  public Timeout timeout() {
    return Timeout.NONE;
  }

  @Override
  public void close() {
    // No-op, handle closed by NamedPipeSocket
  }
}
