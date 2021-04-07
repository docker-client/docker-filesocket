package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnixSocketFactorySupportTest {

  @Test
  @EnabledOnOs(OS.MAC)
  @DisplayName("Supports Mac OS X")
  void supportsMacOsX() {
    assertTrue(new UnixSocketFactorySupport().isSupported("Mac OS X"));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  @DisplayName("Supports Linux")
  void supportsLinux() {
    assertTrue(new UnixSocketFactorySupport().isSupported("Linux"));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  @DisplayName("Doesn't support Windows 10")
  void doesNotSupportWindows10() {
    assertFalse(new UnixSocketFactorySupport().isSupported("Windows 10"));
  }
}
