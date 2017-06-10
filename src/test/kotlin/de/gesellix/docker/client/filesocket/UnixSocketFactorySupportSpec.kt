package de.gesellix.docker.client.filesocket

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object UnixSocketFactorySupportSpec : Spek({

    given("os.name == Mac OS X") {
        val osName = "Mac OS X"
        on("UnixSocketFactory.isSupported('$osName')") {
            val supported = UnixSocketFactorySupport().isSupported(osName)
            it("should return true") {
                assertTrue(supported)
            }
        }
    }

    given("os.name == Linux") {
        val osName = "Linux"
        on("UnixSocketFactory.isSupported('$osName')") {
            val supported = UnixSocketFactorySupport().isSupported(osName)
            it("should return true") {
                assertTrue(supported)
            }
        }
    }

    given("os.name == Windows 10") {
        val osName = "Windows 10"
        on("UnixSocketFactory.isSupported('$osName')") {
            val supported = UnixSocketFactorySupport().isSupported(osName)
            it("should return false") {
                assertFalse(supported)
            }
        }
    }
})
