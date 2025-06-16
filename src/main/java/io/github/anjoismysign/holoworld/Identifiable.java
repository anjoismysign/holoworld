package io.github.anjoismysign.holoworld;

import org.jetbrains.annotations.NotNull;

public interface Identifiable extends Comparable<Identifiable> {
    @NotNull
    String identifier();

    @Override
    default int compareTo(@NotNull Identifiable other) {
        return identifier().compareTo(other.identifier());
    }

}
