package de.gesellix.docker.client.filesocket;

import java.net.Socket;

public class NamedPipeSocketFactory extends FileSocketFactory {

  @Override
  public Socket createSocket() {
    return new NamedPipeSocket();
  }
}
