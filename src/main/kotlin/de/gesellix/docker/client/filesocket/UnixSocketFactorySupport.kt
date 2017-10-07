package de.gesellix.docker.client.filesocket

import mu.KotlinLogging
import org.newsclub.net.unix.AFUNIXSocket

private val log = KotlinLogging.logger {}

class UnixSocketFactorySupport {

    fun isSupported(): Boolean = isSupported(System.getProperty("os.name"))

    fun isSupported(osName: String): Boolean {
        return try {
            val isWindows = osName.toLowerCase().contains("windows")
            !isWindows && AFUNIXSocket.isSupported()
        } catch (reason: Throwable) {
            log.info("unix socket not supported", reason)
            false
        }
    }
}
