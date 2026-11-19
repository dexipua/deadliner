package ua.dexipua.deadliner.feed;

import java.io.IOException;

/** Moodle answered 4xx: the link itself is bad, unlike a timeout or 5xx which may pass. */
public class FeedRejectedException extends IOException {

    public FeedRejectedException(String message) {
        super(message);
    }
}
