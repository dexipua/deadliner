package ua.dexipua.deadliner.bot;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadlinerBotTest {

    private final CommandHandler handler = mock(CommandHandler.class);
    private final TelegramClient client = mock(TelegramClient.class);
    private final DeadlinerBot bot = new DeadlinerBot(handler, client);

    private static Update message(String text) {
        Message message = new Message();
        message.setMessageId(42);
        message.setText(text);
        message.setChat(new Chat(7L, "private"));
        message.setFrom(new User(7L, "Artem", false));
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @Test
    void messageWithTokenIsDeletedEvenWhenHandlingFails() throws Exception {
        when(handler.handle(anyLong(), anyString())).thenThrow(new IllegalStateException("boom"));

        bot.consume(List.of(message("https://teaching.kse.org.ua/x?userid=1&authtoken=abc")));

        verify(client).execute(any(DeleteMessage.class));
    }

    private static List<List<String>> callbacks(org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup m) {
        return m.getKeyboard().stream().map(row -> row.stream().map(b -> b.getText() + "|" + b.getCallbackData()).toList()).toList();
    }

    @Test
    void doneInReminderBecomesUndoAndSnoozeGoes() {
        var markup = TelegramNotifier.keyboard(List.of(List.of(
                new InlineButton("✅ Здано", "done:7"), new InlineButton("⏰ +1 год", "snooze:7"))));

        assertThat(callbacks(DeadlinerBot.toggleDone(markup, 7)))
                .containsExactly(List.of("↩️ Скасувати|undo:7"));
    }

    @Test
    void undoBecomesDoneAgain() {
        var markup = TelegramNotifier.keyboard(List.of(List.of(new InlineButton("↩️ 1. Скасувати", "undo:7"))));

        assertThat(callbacks(DeadlinerBot.toggleDone(markup, 7)))
                .containsExactly(List.of("✅ 1. Здано|done:7"));
    }

    @Test
    void numberedListButtonFlipsAndOthersStay() {
        var markup = TelegramNotifier.keyboard(List.of(List.of(
                new InlineButton("✅ 1", "done:9"), new InlineButton("✅ 2", "done:7"))));

        assertThat(callbacks(DeadlinerBot.toggleDone(markup, 7)))
                .containsExactly(List.of("✅ 1|done:9", "↩️ 2|undo:7"));
    }

    @Test
    void idsThatOnlyEndTheSameAreNotTouched() {
        var markup = TelegramNotifier.keyboard(List.of(List.of(new InlineButton("✅ 1", "done:17"))));

        assertThat(callbacks(DeadlinerBot.toggleDone(markup, 7))).containsExactly(List.of("✅ 1|done:17"));
    }

    @Test
    void replyIsSentForNormalMessage() throws Exception {
        when(handler.handle(anyLong(), anyString())).thenReturn(BotReply.of("hi"));

        bot.consume(List.of(message("/start")));

        verify(client).execute(any(SendMessage.class));
    }
}
