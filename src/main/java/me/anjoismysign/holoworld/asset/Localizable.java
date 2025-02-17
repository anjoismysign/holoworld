package me.anjoismysign.holoworld.asset;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Localizable {
    @Nullable
    String locale();

    default boolean isLocalizable() {
        return locale() != null;
    }

    default boolean matchesLocale(@Nullable String locale) {
        return locale() != null && locale().equalsIgnoreCase(locale);
    }

    default boolean matchesLocale(@NotNull Localizable other) {
        return matchesLocale(other.locale());
    }
}
