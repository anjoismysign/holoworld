package io.github.anjoismysign.holoworld.asset;

import org.jetbrains.annotations.NotNull;

public interface IdentityDataAsset<T extends IdentityDataAsset<T>> extends DataAsset {

    /**
     * Return an IdentityGenerator that creates instances of the same concrete type.
     * For BaseData this will be IdentityGenerator<BaseData>.
     */
    @NotNull
    IdentityGenerator<T> generator();

    /**
     * Default helper: pair the identifier with the generator.
     * Requires implementations to provide identifier().
     */
    @NotNull
    default IdentityGeneration<T> generation() {
        return new IdentityGeneration<>(identifier(), generator());
    }

}
