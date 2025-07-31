package de.gesellix.docker.client.filesocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class FileSocketTest {

  @Test
  public void shouldEncodeHostname() throws Exception {
    final String plainHostname = "for.test";
    try (TestSocket testSocket = new TestSocket()) {
      String encodedHostname = testSocket.encodeHostname(plainHostname);
      assertEquals("666f722e74657374.socket", encodedHostname);
    }
  }

  @Test
  public void shouldDecodeEncodedHostname() throws Exception {
    final String encodedHostname = "666f722e74657374.socket";
    try (TestSocket testSocket = new TestSocket()) {
      String decodedHostname = testSocket.decodeHostname(InetAddress.getByAddress(encodedHostname, new byte[]{0, 0, 0, 1}));
      assertEquals("for.test", decodedHostname);
    }
  }

  @Test
  public void shouldNotDecodePlainHostname() throws Exception {
    final String plainHostname = "for.test";
    try (TestSocket testSocket = new TestSocket()) {
      String decodedHostname = testSocket.decodeHostname(InetAddress.getByAddress(plainHostname, new byte[]{0, 0, 0, 1}));
      assertEquals(plainHostname, decodedHostname);
    }
  }

  static class TestSocket extends FileSocket {
  }

}
