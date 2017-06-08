package de.gesellix.docker.client.filesocket

import groovy.util.logging.Slf4j
import org.newsclub.net.unix.AFUNIXSocket

@Slf4j
class UnixSocketFactory extends FileSocketFactory {

    static boolean isSupported() {
        try {
            def isWindows = System.getProperty("os.name")?.toLowerCase()?.contains("windows")
            return !isWindows && AFUNIXSocket.isSupported()
        }
        catch (Throwable reason) {
            log.info("unix socket not supported", reason)
            return false
        }
    }

    @Override
    Socket createSocket() throws IOException {
        return new UnixSocket()
    }
}
