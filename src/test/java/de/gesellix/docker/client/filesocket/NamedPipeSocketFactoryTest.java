package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedPipeSocketFactoryTest {

  @Test
  void createsNamedPipeSocket() {
    assertTrue(new NamedPipeSocketFactory().createSocket() instanceof NamedPipeSocket);
  }
}
