package de.gesellix.docker.client.filesocket

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.test.assertEquals

class HostnameEncoderSpec : Spek({

    given("a plain URI") {
        val label = "npipe:////./pipe/docker_engine"
        on("HostnameEncoder().encode()") {
            val encoded = HostnameEncoder().encode(label)
            it("should encode the label as base64") {
                assertEquals("6e706970653a2f2f2f2f2e2f706970652f646f636b65725f656e67696e65", encoded)
            }
        }
    }

    given("an encoded URI") {
        val encoded = "6e706970653a2f2f2f2f2e2f706970652f646f636b65725f656e67696e65"
        on("HostnameEncoder().decode()") {
            val label = HostnameEncoder().decode(encoded)
            it("should decode the label from base64") {
                assertEquals("npipe:////./pipe/docker_engine", label)
            }
        }
    }

    given("a long URI") {
        val label = "C:\\Users\\gesellix\\AppData\\Local\\Temp\\named-pipe9191419262972291772.tmp"
        on("HostnameEncoder().encode()") {
            val encoded = HostnameEncoder().encode(label)
            it("should encode, then split long label") {
                assertEquals("433a5c55736572735c676573656c6c69785c417070446174615c4c6f63616c5.c54656d705c6e616d65642d7069706539313931343139323632393732323931.3737322e746d70", encoded)
            }
        }
    }

    given("an encoded and splitted URI") {
        val encoded = "433a5c55736572735c676573656c6c69785c417070446174615c4c6f63616c5.c54656d705c6e616d65642d7069706539313931343139323632393732323931.3737322e746d70"
        on("HostnameEncoder().decode()") {
            val label = HostnameEncoder().decode(encoded)
            it("should decode splitted label") {
                assertEquals("C:\\Users\\gesellix\\AppData\\Local\\Temp\\named-pipe9191419262972291772.tmp", label)
            }
        }
    }
})
