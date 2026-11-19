package ua.dexipua.deadliner.feed;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/** Downloads a feed body. An interface so tests can serve feeds from memory. */
public interface FeedFetcher {

    InputStream fetch(URI uri) throws IOException;
}
