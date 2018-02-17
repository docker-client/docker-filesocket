package de.gesellix.docker.client.filesocket;

import okio.ByteString;

import java.util.ArrayList;
import java.util.List;

class HostnameEncoder {

    /**
     * @see java.net.IDN
     */
    private final static Integer MAX_LABEL_LENGTH = 63;
    private final static Integer MAX_HOSTNAME_LENGTH = MAX_LABEL_LENGTH * 4;

    String encode(String toEncode) {
        String encoded = ByteString.encodeUtf8(toEncode).hex();
        if (encoded.length() > MAX_LABEL_LENGTH && encoded.length() < MAX_HOSTNAME_LENGTH) {
            List<String> labels = new ArrayList<>();
            int labelCount = (int) Math.ceil(encoded.length() / MAX_LABEL_LENGTH.doubleValue());
            for (int step = 0; step < labelCount; step++) {
                int from = step * MAX_LABEL_LENGTH;
                int to = from + MAX_LABEL_LENGTH;
                labels.add(encoded.substring(from, Math.min(to, encoded.length())));
            }
            return String.join(".", labels);
        }
        return encoded;
    }

    String decode(String toDecode) {
        String decoded = toDecode;
        if (toDecode.contains(".")) {
            String[] labels = toDecode.split("\\.");
            decoded = String.join("", labels);
//            decoded = elements.dropLastWhile { it.isEmpty() }.toTypedArray().joinToString("");
        }
        return ByteString.decodeHex(decoded).utf8();
    }
}
