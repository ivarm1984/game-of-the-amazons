package com.amazons.bot;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects every {@link Bot} Spring bean at startup, keyed by the id declared
 * in its {@link BotMetadata}. Adding a new bot to the app means implementing
 * {@code Bot}, annotating it, and registering it as a bean - it then shows up
 * here automatically.
 */
@Component
public class BotRegistry {

    private final Map<String, RegisteredBot> botsById;

    public BotRegistry(List<Bot> bots) {
        Map<String, RegisteredBot> byId = new LinkedHashMap<>();
        for (Bot bot : bots) {
            BotMetadata metadata = bot.getClass().getAnnotation(BotMetadata.class);
            if (metadata == null) {
                throw new IllegalStateException(bot.getClass() + " must be annotated with @BotMetadata");
            }
            RegisteredBot registered = new RegisteredBot(
                    metadata.id(), metadata.displayName(), metadata.difficulty(), metadata.description(), bot);
            RegisteredBot previous = byId.put(registered.id(), registered);
            if (previous != null) {
                throw new IllegalStateException("duplicate bot id: " + registered.id());
            }
        }
        this.botsById = Collections.unmodifiableMap(byId);
    }

    public List<RegisteredBot> listAll() {
        return botsById.values().stream()
                .sorted(Comparator.comparingInt(RegisteredBot::difficulty).thenComparing(RegisteredBot::id))
                .toList();
    }

    public Optional<RegisteredBot> find(String id) {
        return Optional.ofNullable(botsById.get(id));
    }
}
