package io.github.anjoismysign.holoworld.asset;

import org.jetbrains.annotations.NotNull;

public interface IdentityGenerator<T extends DataAsset> {

    @NotNull
    T generate(@NotNull String identifier);

}
