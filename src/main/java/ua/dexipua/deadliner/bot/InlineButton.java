package ua.dexipua.deadliner.bot;

/** A button under a bot message; {@code callbackData} comes back when it is pressed. */
public record InlineButton(String text, String callbackData) {
}
