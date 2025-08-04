package de.gesellix.docker.client.filesocket;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;

public class WindowsNamedPipeLister {

    public static void main(String[] args) {
        List<String> strings = listPipes();
        for (String string : strings) {
            System.out.println(string);
        }
    }

    /**
     * Lists all named pipes currently visible under \\.\pipe\
     *
     * @return list of pipe names (without the \\.\pipe\ prefix)
     */
    public static List<String> listPipes() {
        List<String> pipes = new ArrayList<>();

        WinBase.WIN32_FIND_DATA data = new WinBase.WIN32_FIND_DATA();

        // Call FindFirstFile with the pointer to our struct
        WinNT.HANDLE handle = Kernel32.INSTANCE.FindFirstFile("\\\\.\\pipe\\*", data.getPointer());

        if (WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            return pipes; // no pipes or error
        }

        try {
            data.read(); // populate from native memory
            pipes.add(Native.toString(data.cFileName));

            while (true) {
                boolean success = Kernel32.INSTANCE.FindNextFile(handle, data.getPointer());
                if (!success) {
                    break;
                }
                data.read();
                pipes.add(Native.toString(data.cFileName));
            }
        } finally {
            Kernel32.INSTANCE.FindClose(handle);
        }

        return pipes;
    }

}
