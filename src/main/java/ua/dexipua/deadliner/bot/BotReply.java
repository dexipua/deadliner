package ua.dexipua.deadliner.bot;

import java.util.List;

/**
 * What the bot answers. {@code deleteUserMessage} is set when the user's message held their
 * Moodle link: it contains a personal token, so it should not stay in the chat history.
 */
public record BotReply(String text, List<InlineButton> buttons, boolean deleteUserMessage) {

    static BotReply of(String text) {
        return new BotReply(text, List.of(), false);
    }

    static BotReply withButtons(String text, List<InlineButton> buttons) {
        return new BotReply(text, buttons, false);
    }
}
