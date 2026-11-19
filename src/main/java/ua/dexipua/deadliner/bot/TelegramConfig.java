package ua.dexipua.deadliner.bot;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Wires the bot by hand: the telegrambots Spring Boot starter targets Boot 3.
 * Tests switch it off with deadliner.telegram.enabled=false.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "deadliner.telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramConfig {

    @Bean
    TelegramClient telegramClient(@Value("${deadliner.telegram.token}") String token) {
        return new OkHttpTelegramClient(token);
    }

    @Bean
    Notifier telegramNotifier(TelegramClient client) {
        return new TelegramNotifier(client);
    }

    @Bean(destroyMethod = "close")
    TelegramBotsLongPollingApplication telegramBots(@Value("${deadliner.telegram.token}") String token,
                                                    CommandHandler handler, TelegramClient client)
            throws TelegramApiException {
        TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
        app.registerBot(token, new DeadlinerBot(handler, client));
        registerCommandMenu(client);
        return app;
    }

    /** The ☰ menu next to the message field. */
    private static void registerCommandMenu(TelegramClient client) {
        try {
            client.execute(SetMyCommands.builder().commands(List.of(
                    new BotCommand("deadlines", "Дедлайни на 7 днів"),
                    new BotCommand("stats", "Моя статистика"),
                    new BotCommand("settings", "Нагадування й тихі години"),
                    new BotCommand("group", "Мої групи по курсах"),
                    new BotCommand("help", "Як підключити Moodle"),
                    new BotCommand("stop", "Видалити мої дані"))).build());
        } catch (TelegramApiException e) {
            LoggerFactory.getLogger(TelegramConfig.class).warn("Could not set the command menu: {}", e.getMessage());
        }
    }
}
