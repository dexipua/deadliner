package ua.dexipua.deadliner.bot;

import java.util.List;

/**
 * What the bot answers.
 *
 * @param buttons           rows of inline buttons
 * @param deleteUserMessage the user's message held their Moodle link (a personal token), so delete it
 * @param mode              SEND a new message, EDIT the message whose button was pressed, or show a short TOAST
 * @param clearButtonsFor   with TOAST: remove this deadline's buttons from the pressed message
 */
public record BotReply(String text, List<List<InlineButton>> buttons, boolean deleteUserMessage, Mode mode,
                       Long clearButtonsFor) {

    public enum Mode { SEND, EDIT, TOAST }

    static BotReply of(String text) {
        return new BotReply(text, List.of(), false, Mode.SEND, null);
    }

    static BotReply withButtons(String text, List<List<InlineButton>> buttons) {
        return new BotReply(text, buttons, false, Mode.SEND, null);
    }

    static BotReply edit(String text, List<List<InlineButton>> buttons) {
        return new BotReply(text, buttons, false, Mode.EDIT, null);
    }

    static BotReply toast(String text, Long clearButtonsFor) {
        return new BotReply(text, List.of(), false, Mode.TOAST, clearButtonsFor);
    }

    static BotReply deletingUserMessage(String text) {
        return new BotReply(text, List.of(), true, Mode.SEND, null);
    }
}
