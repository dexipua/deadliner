package ua.dexipua.deadliner.feed;

/** The feed body is not an iCal calendar, e.g. Moodle's "Invalid authentication" page. */
public class InvalidFeedException extends Exception {

    public InvalidFeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
