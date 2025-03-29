package me.anjoismysign.holoworld.asset;

import org.jetbrains.annotations.NotNull;

public record IdentityGeneration<T extends DataAsset>(
        @NotNull String identifier,
        @NotNull IdentityGenerator<T> generator) implements DataAsset {

    public T asset() {
        return generator.generate(identifier);
    }
}
