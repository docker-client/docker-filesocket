package de.gesellix.docker.client.filesocket

import mu.KotlinLogging
import org.newsclub.net.unix.AFUNIXSocket

private val log = KotlinLogging.logger {}

class UnixSocketFactorySupport {

    fun isSupported(): Boolean {
        return isSupported(System.getProperty("os.name"))
    }

    fun isSupported(osName: String): Boolean {
        try {
            val isWindows = osName.toLowerCase().contains("windows")
            return !isWindows && AFUNIXSocket.isSupported()
        } catch (reason: Throwable) {
            log.info("unix socket not supported", reason)
            return false
        }
    }
}
