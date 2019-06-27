package de.gesellix.docker.client.filesocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostnameEncoderTest {

    @Test
    @DisplayName("Should encode a URI as base64")
    void encodePlainURI(TestInfo testInfo) {
        String label = "npipe:////./pipe/docker_engine";
        String encoded = new HostnameEncoder().encode(label);
        assertEquals("6e706970653a2f2f2f2f2e2f706970652f646f636b65725f656e67696e65", encoded);
    }

    @Test
    @DisplayName("Should decode a base64 encoded URI")
    void decodeBase64URI(TestInfo testInfo) {
        String encoded = "6e706970653a2f2f2f2f2e2f706970652f646f636b65725f656e67696e65";
        String label = new HostnameEncoder().decode(encoded);
        assertEquals("npipe:////./pipe/docker_engine", label);
    }

    @Test
    @DisplayName("Should encode a long URI as splitted base64")
    void encodeLongURI(TestInfo testInfo) {
        String label = "C:\\Users\\gesellix\\AppData\\Local\\Temp\\named-pipe9191419262972291772.tmp";
        String encoded = new HostnameEncoder().encode(label);
        assertEquals("433a5c55736572735c676573656c6c69785c417070446174615c4c6f63616c5.c54656d705c6e616d65642d7069706539313931343139323632393732323931.3737322e746d70", encoded);
    }

    @Test
    @DisplayName("Should decode a splitted base64 encoded URI")
    void decodeSplittedBase64URI(TestInfo testInfo) {
        String encoded = "433a5c55736572735c676573656c6c69785c417070446174615c4c6f63616c5.c54656d705c6e616d65642d7069706539313931343139323632393732323931.3737322e746d70";
        String label = new HostnameEncoder().decode(encoded);
        assertEquals("C:\\Users\\gesellix\\AppData\\Local\\Temp\\named-pipe9191419262972291772.tmp", label);
    }
}
