package ua.dexipua.deadliner.bot;

import org.springframework.beans.factory.annotation.Value;
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
        return app;
    }
}
