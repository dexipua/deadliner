package ua.dexipua.deadliner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ua.dexipua.deadliner.feed.FeedUrlCipher;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FeedUrlCipher feedUrlCipher(@Value("${deadliner.feed-key}") String base64Key) {
        return new FeedUrlCipher(base64Key);
    }

    /** Tests switch scheduling off so jobs only run when a test calls them. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "deadliner.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {
    }
}
