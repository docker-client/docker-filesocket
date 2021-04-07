package de.gesellix.docker.client.filesocket;

import org.newsclub.net.unix.AFUNIXSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnixSocketFactorySupport {

  private static final Logger log = LoggerFactory.getLogger(UnixSocketFactorySupport.class);

  public boolean isSupported() {
    return isSupported(System.getProperty("os.name"));
  }

  public boolean isSupported(String osName) {
    try {
      boolean isWindows = osName.toLowerCase().contains("windows");
      return !isWindows && AFUNIXSocket.isSupported();
    }
    catch (Throwable reason) {
      log.info("Unix socket not supported", reason);
      return false;
    }
  }
}
