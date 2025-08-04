package de.gesellix.docker.client.filesocket;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

public interface ExtendedKernel32 extends Library {

    ExtendedKernel32 INSTANCE = Native.load("kernel32", ExtendedKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean GetOverlappedResult(
      WinNT.HANDLE hFile,
      WinBase.OVERLAPPED lpOverlapped,
      IntByReference lpNumberOfBytesTransferred,
      boolean bWait
    );

    /**
     * Cancels all pending I/O operations on the specified file handle.
     *
     * @param hFile        The handle to the file or I/O device.
     * @param lpOverlapped Reserved; should be NULL.
     * @return true if successful; false otherwise.
     */
    boolean CancelIoEx(
      WinNT.HANDLE hFile,
      Pointer lpOverlapped
    );

    WinDef.DWORD WaitForSingleObject(
      WinNT.HANDLE hHandle,
      int dwMilliseconds
    );

    boolean CloseHandle(
      WinNT.HANDLE hObject
    );
}
