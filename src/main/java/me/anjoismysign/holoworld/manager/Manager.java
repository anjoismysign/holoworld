package me.anjoismysign.holoworld.manager;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.logging.Logger;

public interface Manager {

    @Nullable
    Logger logger();

    default void ifLogger(Consumer<Logger> consumer) {
        @Nullable Logger logger = logger();
        if (logger == null)
            return;
        consumer.accept(logger);
    }

    void reload();

}
