package de.gesellix.docker.client.filesocket;

import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public final class NamedPipeUtils {

    private NamedPipeUtils() {
    }

    public static boolean readToBuffer(HANDLE handle, byte[] buf, IntByReference bytesRead) {
        return Kernel32.INSTANCE.ReadFile(handle, buf, buf.length, bytesRead, null);
    }

    public static boolean writeFromBuffer(HANDLE handle, byte[] buf, int len, IntByReference written) {
        return Kernel32.INSTANCE.WriteFile(handle, buf, len, written, null);
    }

    public static boolean readOverlapped(WinNT.HANDLE handle, byte[] buf, IntByReference bytesRead, int timeoutMillis) {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);

        boolean ok = Kernel32.INSTANCE.ReadFile(handle, buf, buf.length, null, overlapped);
        if (!ok) {
            int err = Kernel32.INSTANCE.GetLastError();
            if (err != WinError.ERROR_IO_PENDING) {
                Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
                return false;
            }

            int wait = Kernel32.INSTANCE.WaitForSingleObject(overlapped.hEvent, timeoutMillis);
            if (wait != WinBase.WAIT_OBJECT_0) {
                ExtendedKernel32.INSTANCE.CancelIoEx(handle, overlapped.getPointer());
                Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
                return false;
            }
        }

        ExtendedKernel32.INSTANCE.GetOverlappedResult(handle, overlapped, bytesRead, false);
        Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
        return bytesRead.getValue() > 0;
    }

    public static boolean writeOverlapped(
      WinNT.HANDLE handle, byte[] buf, int len, IntByReference bytesWritten, int timeoutMillis) {
        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);

        boolean ok = Kernel32.INSTANCE.WriteFile(handle, buf, len, null, overlapped);
        if (!ok) {
            int err = Kernel32.INSTANCE.GetLastError();
            if (err != WinError.ERROR_IO_PENDING) {
                Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
                return false;
            }

            int wait = Kernel32.INSTANCE.WaitForSingleObject(overlapped.hEvent, timeoutMillis);
            if (wait != WinBase.WAIT_OBJECT_0) {
                ExtendedKernel32.INSTANCE.CancelIoEx(handle, overlapped.getPointer());
                Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
                return false;
            }
        }

        ExtendedKernel32.INSTANCE.GetOverlappedResult(handle, overlapped, bytesWritten, false);
        Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
        return bytesWritten.getValue() > 0;
    }

    public static void closeHandle(HANDLE handle) {
        if (handle != null && !INVALID_HANDLE_VALUE.equals(handle)) {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }
}
