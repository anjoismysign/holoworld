package me.anjoismysign.holoworld.asset;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface DataAssetEntry<T extends DataAsset> {
    @NotNull
    File file();

    @NotNull
    T asset();
}
