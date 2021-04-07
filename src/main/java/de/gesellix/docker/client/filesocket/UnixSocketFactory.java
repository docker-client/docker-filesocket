package de.gesellix.docker.client.filesocket;

import java.net.Socket;

public class UnixSocketFactory extends FileSocketFactory {

  @Override
  public Socket createSocket() {
    return new UnixSocket();
  }
}
