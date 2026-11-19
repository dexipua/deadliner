package ua.dexipua.deadliner.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/** Receives updates from Telegram (long polling) and passes them to {@link CommandHandler}. */
public class DeadlinerBot implements LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadlinerBot.class);

    private final CommandHandler handler;
    private final TelegramClient client;

    public DeadlinerBot(CommandHandler handler, TelegramClient client) {
        this.handler = handler;
        this.client = client;
    }

    // updates are handled one by one, in order: the bot is small and this keeps the logic simple
    @Override
    public void consume(List<Update> updates) {
        updates.forEach(this::consume);
    }

    private void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                onMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                onCallback(update.getCallbackQuery());
            }
        } catch (TelegramApiRequestException e) {
            if (e.getApiResponse() != null && e.getApiResponse().contains("message is not modified")) {
                // a fast double tap re-sends the same keyboard; nothing to fix
                log.debug("Update {}: message not modified", update.getUpdateId());
            } else {
                log.error("Telegram rejected a reply to update {}: {}", update.getUpdateId(), e.getMessage());
            }
        } catch (RuntimeException | TelegramApiException e) {
            // one bad update must not stop the bot for everyone
            log.error("Failed to handle update {}", update.getUpdateId(), e);
        }
    }

    private void onMessage(Message message) throws TelegramApiException {
        BotReply reply;
        try {
            reply = handler.handle(message.getFrom().getId(), message.getText());
        } finally {
            // delete a message with a token even if handling it failed
            if (CommandHandler.containsToken(message.getText())) {
                delete(message);
            }
        }
        if (reply.deleteUserMessage() && !CommandHandler.containsToken(message.getText())) {
            delete(message);
        }
        client.execute(TelegramNotifier.message(message.getChatId(), reply.text(), reply.buttons()));
    }

    private void onCallback(CallbackQuery query) throws TelegramApiException {
        BotReply reply = handler.handleCallback(query.getFrom().getId(), query.getData());
        long chatId = query.getFrom().getId();
        boolean hasMessage = query.getMessage() instanceof Message;
        switch (reply.mode()) {
            case TOAST -> {
                client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).text(reply.text()).build());
                if (reply.toggleButtonsFor() != null && query.getMessage() instanceof Message pressed
                        && pressed.getReplyMarkup() != null) {
                    client.execute(EditMessageReplyMarkup.builder()
                            .chatId(pressed.getChatId())
                            .messageId(pressed.getMessageId())
                            .replyMarkup(toggleDone(pressed.getReplyMarkup(), reply.toggleButtonsFor()))
                            .build());
                }
            }
            case EDIT -> {
                client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).build());
                if (hasMessage) {
                    Message pressed = (Message) query.getMessage();
                    client.execute(EditMessageText.builder()
                            .chatId(pressed.getChatId())
                            .messageId(pressed.getMessageId())
                            .text(reply.text())
                            .parseMode(ParseMode.HTML)
                            .replyMarkup(reply.buttons().isEmpty() ? null : TelegramNotifier.keyboard(reply.buttons()))
                            .build());
                } else {
                    client.execute(TelegramNotifier.message(chatId, reply.text(), reply.buttons()));
                }
            }
            case SEND -> {
                client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).build());
                client.execute(TelegramNotifier.message(chatId, reply.text(), reply.buttons()));
            }
        }
    }

    /**
     * Flips one deadline's button: "✅ … Здано" (done:id) ⇄ "↩️ … Скасувати" (undo:id).
     * Its "+1 год" button goes away once it is done. Other deadlines' buttons stay as they are.
     */
    static InlineKeyboardMarkup toggleDone(InlineKeyboardMarkup markup, long deadlineId) {
        String done = "done:" + deadlineId;
        String undo = "undo:" + deadlineId;
        List<InlineKeyboardRow> rows = markup.getKeyboard().stream()
                .map(row -> new InlineKeyboardRow(row.stream()
                        .filter(b -> !("snooze:" + deadlineId).equals(b.getCallbackData()))
                        .map(b -> {
                            if (done.equals(b.getCallbackData())) {
                                return button(b.getText().replace("✅", "↩️").replace("Здано", "Скасувати"), undo);
                            }
                            if (undo.equals(b.getCallbackData())) {
                                return button(b.getText().replace("↩️", "✅").replace("Скасувати", "Здано"), done);
                            }
                            return b;
                        })
                        .toList()))
                .filter(row -> !row.isEmpty())
                .toList();
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private void delete(Message message) {
        try {
            client.execute(new DeleteMessage(message.getChatId().toString(), message.getMessageId()));
        } catch (TelegramApiException e) {
            log.warn("Could not delete message with a feed link: {}", e.getMessage());
        }
    }
}
