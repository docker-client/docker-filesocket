package de.gesellix.docker.client.filesocket;

import org.jetbrains.annotations.NotNull;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import okio.Buffer;
import okio.Source;
import okio.Timeout;

public class NamedPipeSource implements Source {

  private final WinNT.HANDLE handle;
  private final int timeoutMillis;

  public NamedPipeSource(WinNT.HANDLE handle, Timeout timeout) {
    this.handle = handle;
    this.timeoutMillis = (int) timeout.timeoutNanos() / 1000;
  }

  @Override
  public long read(@NotNull Buffer sink, long byteCount) {
    if (byteCount < 0) {
      throw new IllegalArgumentException("Invalid buffer size: " + byteCount);
    }
    if (byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Can only read max " + Integer.MAX_VALUE + " bytes");
    }

    if (byteCount == 0) {
      return 0;
    }

    byte[] data = new byte[(int) byteCount];
    IntByReference bytesRead = new IntByReference();
    //boolean ok = NamedPipeUtils.readToBuffer(handle, data, bytesRead);
    boolean ok = NamedPipeUtils.readOverlapped(handle, data, bytesRead, timeoutMillis);
    if (!ok) {
      int err = Kernel32.INSTANCE.GetLastError();
      if (err == WinError.ERROR_OPERATION_ABORTED) {
        // Expected when CancelIoEx() is called during close()
        return -1;
      }
      return err == 0 ? 0 : -1; // Other read error
    }

    if (bytesRead.getValue() < 0) {
      return -1; // EOF
    }

    sink.write(data, 0, bytesRead.getValue());
    sink.flush();
    return bytesRead.getValue();
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
