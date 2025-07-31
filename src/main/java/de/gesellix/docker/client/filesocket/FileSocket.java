package de.gesellix.docker.client.filesocket;

import java.net.InetAddress;
import java.net.Socket;

public abstract class FileSocket extends Socket {

  public final static String SOCKET_MARKER = ".socket";

  public String encodeHostname(String hostname) {
    return new HostnameEncoder().encode(hostname) + SOCKET_MARKER;
  }

  public String decodeHostname(InetAddress address) {
    String hostName = address.getHostName();
    if (!hostName.endsWith(SOCKET_MARKER)) {
      return hostName;
    }
    return new HostnameEncoder().decode(hostName.substring(0, hostName.indexOf(SOCKET_MARKER)));
  }
}
