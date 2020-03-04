package de.gesellix.docker.client.filesocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

class NamedPipeDelegatingOutputStream extends OutputStream {

    private final RandomAccessFile namedPipe;

    public NamedPipeDelegatingOutputStream(RandomAccessFile namedPipe) {
        this.namedPipe = namedPipe;
    }

    @Override
    public void write(int b) throws IOException {
        namedPipe.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        namedPipe.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        namedPipe.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        namedPipe.close();
        super.close();
    }
}
