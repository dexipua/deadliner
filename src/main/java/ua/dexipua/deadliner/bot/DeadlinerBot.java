package ua.dexipua.deadliner.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
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
        } catch (RuntimeException | TelegramApiException e) {
            // one bad update must not stop the bot for everyone
            log.error("Failed to handle update {}", update.getUpdateId(), e);
        }
    }

    private void onMessage(Message message) throws TelegramApiException {
        BotReply reply = handler.handle(message.getFrom().getId(), message.getText());
        if (reply.deleteUserMessage()) {
            try {
                client.execute(new DeleteMessage(message.getChatId().toString(), message.getMessageId()));
            } catch (TelegramApiException e) {
                log.warn("Could not delete message with a feed link: {}", e.getMessage());
            }
        }
        send(message.getChatId(), reply);
    }

    private void onCallback(CallbackQuery query) throws TelegramApiException {
        BotReply reply = handler.handleCallback(query.getFrom().getId(), query.getData());
        client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).build());
        send(query.getFrom().getId(), reply);
    }

    private void send(long chatId, BotReply reply) throws TelegramApiException {
        SendMessage.SendMessageBuilder<?, ?> message = SendMessage.builder().chatId(chatId).text(reply.text());
        if (!reply.buttons().isEmpty()) {
            message.replyMarkup(TelegramNotifier.keyboard(reply.buttons()));
        }
        client.execute(message.build());
    }
}
