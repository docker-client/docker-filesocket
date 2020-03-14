package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.net.InetSocketAddress;

import static java.net.InetAddress.getByAddress;

class NamedPipeSocketTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void canConnect() throws IOException {
        NamedPipeSocket namedPipeSocket = new NamedPipeSocket();
        namedPipeSocket.connect(new InetSocketAddress(getByAddress(namedPipeSocket.encodeHostname("//./pipe/docker_engine"), new byte[] {0, 0, 0, 0}), 0));
    }
}
