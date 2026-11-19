package ua.dexipua.deadliner.bot;

import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/** Sends HTML messages through the Telegram Bot API. */
public class TelegramNotifier implements Notifier {

    private static final int FORBIDDEN = 403;

    private final TelegramClient client;

    public TelegramNotifier(TelegramClient client) {
        this.client = client;
    }

    @Override
    public void send(long telegramId, String html, List<List<InlineButton>> buttons) {
        try {
            client.execute(message(telegramId, html, buttons));
        } catch (TelegramApiRequestException e) {
            if (e.getErrorCode() != null && e.getErrorCode() == FORBIDDEN) {
                throw new UserBlockedException(telegramId);
            }
            throw new IllegalStateException("Telegram rejected the message: " + e.getMessage(), e);
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Could not reach Telegram: " + e.getMessage(), e);
        }
    }

    static SendMessage message(long chatId, String html, List<List<InlineButton>> buttons) {
        SendMessage.SendMessageBuilder<?, ?> message = SendMessage.builder()
                .chatId(chatId)
                .text(html)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true);
        if (!buttons.isEmpty()) {
            message.replyMarkup(keyboard(buttons));
        }
        return message.build();
    }

    static InlineKeyboardMarkup keyboard(List<List<InlineButton>> rows) {
        return InlineKeyboardMarkup.builder()
                .keyboard(rows.stream()
                        .map(row -> new InlineKeyboardRow(row.stream()
                                .map(b -> InlineKeyboardButton.builder().text(b.text()).callbackData(b.callbackData()).build())
                                .toList()))
                        .toList())
                .build();
    }
}
