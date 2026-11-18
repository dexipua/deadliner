package ua.dexipua.deadliner.feed;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedUrlCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String URL =
            "https://teaching.kse.org.ua/calendar/export_execute.php?userid=1&authtoken=abc";

    private final FeedUrlCipher cipher = new FeedUrlCipher(KEY);

    @Test
    void roundTrip() {
        assertThat(cipher.decrypt(cipher.encrypt(URL))).isEqualTo(URL);
    }

    @Test
    void sameInputDifferentCiphertext() {
        assertThat(cipher.encrypt(URL)).isNotEqualTo(cipher.encrypt(URL));
    }

    @Test
    void tamperedDataFails() {
        byte[] data = cipher.encrypt(URL);
        data[data.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(data)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyLengthRejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new FeedUrlCipher(shortKey)).isInstanceOf(IllegalArgumentException.class);
    }
}
