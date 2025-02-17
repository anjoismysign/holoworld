package me.anjoismysign.holoworld.asset;

import org.jetbrains.annotations.NotNull;

public interface AssetGenerator<T extends DataAsset> extends DataAsset {

    @NotNull
    T generate();

}
