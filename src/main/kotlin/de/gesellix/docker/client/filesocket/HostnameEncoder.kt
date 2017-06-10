package de.gesellix.docker.client.filesocket

import okio.ByteString

class HostnameEncoder {

    companion object {

        /**
         * @see java.net.IDN
         */
        const val MAX_LABEL_LENGTH = 63
        const val MAX_HOSTNAME_LENGTH = MAX_LABEL_LENGTH * 4
    }

    fun encode(toEncode: String): String {
        val encoded = ByteString.encodeUtf8(toEncode).hex()
        if (encoded.length > MAX_LABEL_LENGTH && encoded.length < MAX_HOSTNAME_LENGTH) {
            val labelCount = Math.ceil((encoded.length / MAX_LABEL_LENGTH).toDouble()).toInt()
            val labels = (0..labelCount).fold(ArrayList<String>()) { substrings, step ->
                val from = step * MAX_LABEL_LENGTH
                val to = from + MAX_LABEL_LENGTH
                substrings.add(encoded.substring(from, Math.min(to, encoded.length)))
                substrings
            }
            return labels.joinToString(".")
        }
        return encoded
    }

    fun decode(toDecode: String): String {
        var decoded = toDecode
        if (toDecode.contains(".")) {
            decoded = toDecode.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().joinToString("")
        }
        return ByteString.decodeHex(decoded).utf8()
    }
}
