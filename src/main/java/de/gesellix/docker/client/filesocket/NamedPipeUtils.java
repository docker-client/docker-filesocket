package de.gesellix.docker.client.filesocket;

import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public final class NamedPipeUtils {

  private static final Logger log = LoggerFactory.getLogger(NamedPipeUtils.class);

  private NamedPipeUtils() {
  }

  public static WinNT.HANDLE connect(
      String path,
      int totalTimeoutMs,        // z.B. 15000
      int busyWaitCapMs,         // z.B. 2000 (max für einzelne WaitNamedPipe)
      int notFoundBaseSleepMs    // z.B. 50
  ) {
    final long deadline = System.nanoTime() + (long) totalTimeoutMs * 1_000_000L;
    int notFoundBackoff = notFoundBaseSleepMs;
    WinNT.HANDLE h;

    while (true) {
      h = Kernel32.INSTANCE.CreateFile(
          path,
          WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
          0,
          null,
          WinNT.OPEN_EXISTING,
          WinNT.FILE_FLAG_OVERLAPPED,
          null
      );

      if (!WinBase.INVALID_HANDLE_VALUE.equals(h)) {
        // Verbunden – Handle ist sofort nutzbar (für Overlapped-I/O)
        log.debug("connected");
        return h;
      }

      int err = Kernel32.INSTANCE.GetLastError();
      long remainingMs = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
      if (remainingMs == 0) {
        log.debug("Connect timed out; last error=" + err);
        throw new RuntimeException("Connect timed out; last error=" + err);
      }

      if (err == WinError.ERROR_PIPE_BUSY) {
        log.debug("Pipe busy; last error=" + err);
        // Warte, aber maximal busyWaitCapMs und nie länger als verbleibende Zeit
        int to = (int) Math.min(remainingMs, busyWaitCapMs);
        boolean ok = Kernel32.INSTANCE.WaitNamedPipe(path, to);
        if (!ok) {
          int e2 = Kernel32.INSTANCE.GetLastError(); // z.B. ERROR_SEM_TIMEOUT
          if (e2 == WinError.ERROR_SEM_TIMEOUT && (deadline - System.nanoTime()) > 0) {
            log.debug("Retry wait; last error=" + err);
            continue; // Restzeit vorhanden → nochmal versuchen
          }
          throw new RuntimeException("WaitNamedPipe failed: " + e2);
        }
        // danach erneut versuchen
        continue;
      }

      if (err == WinError.ERROR_FILE_NOT_FOUND) {
        log.debug("File not found; last error=" + err);
        // Server hat Pipe noch nicht erstellt → kurzer, exponentieller Backoff
        int sleepMs = Math.min(notFoundBackoff, (int) remainingMs);
        try {
          Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for pipe", ie);
        }
        notFoundBackoff = Math.min(notFoundBackoff * 2, 250); // Deckel bei 250ms
        continue;
      }

      // Andere Fehler → sofort abbrechen
      throw new RuntimeException("CreateFile failed: " + err);
    }
  }

  public static boolean readOverlapped(WinNT.HANDLE handle, byte[] buf, IntByReference bytesRead, int timeoutMillis) {
    WinNT.HANDLE evt = Kernel32.INSTANCE.CreateEvent(null, false, false, null);
    if (evt == null || WinBase.INVALID_HANDLE_VALUE.equals(evt)) {
      throw new RuntimeException("CreateEvent failed: " + Kernel32.INSTANCE.GetLastError());
    }

    WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
    overlapped.hEvent = evt;
//    overlapped.hEvent = evt;
    overlapped.write(); // wichtig: Struktur in nativen Speicher schreiben

//    try {
//      Thread.sleep(500);
//    } catch (InterruptedException ie) {
//      Thread.currentThread().interrupt();
//      throw new RuntimeException("Interrupted while waiting for pipe", ie);
//    }

    try {
      boolean ok = Kernel32.INSTANCE.ReadFile(handle, buf, buf.length, null, overlapped);
      if (!ok) {
        int err = Kernel32.INSTANCE.GetLastError();
        if (err != WinError.ERROR_IO_PENDING) {
          log.debug("IO pending; last error=" + err);
          return false;
        }

        int wait = Kernel32.INSTANCE.WaitForSingleObject(evt, timeoutMillis);
        if (wait != WinBase.WAIT_OBJECT_0) {
          log.debug("File not found; last error=" + err);
          ExtendedKernel32.INSTANCE.CancelIoEx(handle, overlapped.getPointer());
          return false;
        }
      }


      // Ergebnis (auch bei synchroner Completion) abholen – ohne zusätzlich zu warten
      if (!ExtendedKernel32.INSTANCE.GetOverlappedResult(handle, overlapped, bytesRead, false)) {
        int err = Kernel32.INSTANCE.GetLastError();
        log.debug("Failed to get overlapped result; last error=" + err);
        return false;
      }

      int n = bytesRead.getValue();
      log.debug("Bytes read: {}", bytesRead.getValue());
      // n == 0: EOF
      return n >= 0;
    } finally {
      closeHandle(evt);
    }
  }

  /**
   * Overlapped-Write: schreibt buffer[0..len) vollständig oder wirft bei Timeout/Fehler.
   * Beachtet Teil-Schreibungen und verwendet eine Gesamt-Deadline (timeoutMs).
   */
  public static boolean writeOverlapped(
      WinNT.HANDLE handle, byte[] buf, int len, IntByReference bytesWritten, int timeoutMs) {
    if (len < 0 || len > buf.length) {
      throw new IllegalArgumentException("len out of range");
    }
    if (len == 0) {
      return true;
    }

//    try {
//      Thread.sleep(500);
//    } catch (InterruptedException ie) {
//      Thread.currentThread().interrupt();
//      throw new RuntimeException("Interrupted while waiting for pipe", ie);
//    }

    final long deadline = System.nanoTime() + (long) timeoutMs * 1_000_000L;

    int totalWritten = 0;

    while (totalWritten < len) {
      long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
      if (remainingMs <= 0) {
        throw new RuntimeException("writeOverlapped: overall timeout; written=" + totalWritten + "/" + len);
      }

      // Frisches auto-reset Event pro Operation
      WinNT.HANDLE evt = Kernel32.INSTANCE.CreateEvent(null, false, false, null);
      if (evt == null || WinBase.INVALID_HANDLE_VALUE.equals(evt)) {
        throw new RuntimeException("CreateEvent failed: " + Kernel32.INSTANCE.GetLastError());
      }

      WinBase.OVERLAPPED ol = new WinBase.OVERLAPPED();
      ol.hEvent = evt;
      ol.write();

      try {
        boolean ok = Kernel32.INSTANCE.WriteFile(handle, buf, len, null, ol);
        if (!ok) {
          int err = Kernel32.INSTANCE.GetLastError();

          // Sofortige Fehler
          if (err != WinError.ERROR_IO_PENDING) {
            if (err == WinError.ERROR_NO_DATA || err == WinError.ERROR_BROKEN_PIPE) {
              throw new RuntimeException("writeOverlapped: pipe closed by peer");
            }
            throw new RuntimeException("WriteFile failed: " + err);
          }

          // Asynchron → warten bis fertig oder Timeout
          int wait = Kernel32.INSTANCE.WaitForSingleObject(evt, (int) Math.min(Integer.MAX_VALUE, remainingMs));
          if (wait != WinBase.WAIT_OBJECT_0) {
            // Timeout/Fehler → I/O abbrechen
            ExtendedKernel32.INSTANCE.CancelIoEx(handle, ol.getPointer());
            // Optional: warten, bis Cancel durch ist (nicht zwingend)
            throw new RuntimeException("writeOverlapped: wait failed/timeout (Wait=" + wait + ")");
          }
        }

        // Ergebnis abholen (blockiert nicht, weil Event signalisiert)
        if (!ExtendedKernel32.INSTANCE.GetOverlappedResult(handle, ol, bytesWritten, false)) {
          int e2 = Kernel32.INSTANCE.GetLastError();
          if (e2 == WinError.ERROR_OPERATION_ABORTED) {
            throw new RuntimeException("writeOverlapped: operation aborted");
          }
          throw new RuntimeException("GetOverlappedResult(write) failed: " + e2);
        }

        int n = bytesWritten.getValue();
        if (n < 0) {
          throw new RuntimeException("writeOverlapped: negative bytesWritten? " + n);
        }
        if (n == 0) {
          // Möglich, wenn Gegenstelle zu ist → als Fehler behandeln
          throw new RuntimeException("writeOverlapped: wrote 0 bytes, likely peer closed");
        }

        totalWritten += n;
      } finally {
        closeHandle(evt);
      }
    }
//    return totalWritten >= 0;
    return true;
  }

  public static boolean readToBuffer(HANDLE handle, byte[] buf, IntByReference bytesRead) {
    return Kernel32.INSTANCE.ReadFile(handle, buf, buf.length, bytesRead, null);
  }

  public static boolean writeFromBuffer(HANDLE handle, byte[] buf, int len, IntByReference written) {
    return Kernel32.INSTANCE.WriteFile(handle, buf, len, written, null);
  }

  public static void closeHandle(HANDLE handle) {
    if (handle != null && !INVALID_HANDLE_VALUE.equals(handle)) {
      Kernel32.INSTANCE.CloseHandle(handle);
    }
  }
}
