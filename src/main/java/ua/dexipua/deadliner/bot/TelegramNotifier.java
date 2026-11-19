package ua.dexipua.deadliner.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/** Sends messages through the Telegram Bot API; one button per row. */
public class TelegramNotifier implements Notifier {

    private static final int FORBIDDEN = 403;

    private final TelegramClient client;

    public TelegramNotifier(TelegramClient client) {
        this.client = client;
    }

    @Override
    public void send(long telegramId, String text, List<InlineButton> buttons) {
        SendMessage.SendMessageBuilder<?, ?> message = SendMessage.builder().chatId(telegramId).text(text);
        if (!buttons.isEmpty()) {
            message.replyMarkup(keyboard(buttons));
        }
        try {
            client.execute(message.build());
        } catch (TelegramApiRequestException e) {
            if (e.getErrorCode() != null && e.getErrorCode() == FORBIDDEN) {
                throw new UserBlockedException(telegramId);
            }
            throw new IllegalStateException("Telegram rejected the message: " + e.getMessage(), e);
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Could not reach Telegram: " + e.getMessage(), e);
        }
    }

    static InlineKeyboardMarkup keyboard(List<InlineButton> buttons) {
        List<InlineKeyboardRow> rows = buttons.stream()
                .map(b -> new InlineKeyboardRow(InlineKeyboardButton.builder()
                        .text(b.text())
                        .callbackData(b.callbackData())
                        .build()))
                .toList();
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}
