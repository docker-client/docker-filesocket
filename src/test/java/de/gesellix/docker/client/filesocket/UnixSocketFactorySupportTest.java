package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnixSocketFactorySupportTest {

    @Test
    @DisplayName("Supports Mac OS X")
    void supportsMacOsX() {
        assertTrue(new UnixSocketFactorySupport().isSupported("Mac OS X"));
    }

    @Test
    @DisplayName("Supports Linux")
    void supportsLinux() {
        assertTrue(new UnixSocketFactorySupport().isSupported("Linux"));
    }

    @Test
    @DisplayName("Doesn't support Windows 10")
    void doesNotSupportWindows10() {
        assertFalse(new UnixSocketFactorySupport().isSupported("Windows 10"));
    }
}
