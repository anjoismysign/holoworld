package me.anjoismysign.holoworld.manager;

import me.anjoismysign.holoworld.asset.AssetGenerator;
import me.anjoismysign.holoworld.asset.DataAsset;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.logging.Logger;

public interface ManagerFactory {

    @NotNull
    String defaultLocale();

    /**
     * Creates an asset manager for the specified asset class and parent directory.
     * Needs to be reloaded manually.
     * Useful in case of looking for instantiation while delaying assets loading.
     *
     * @param <T>             the type of data asset
     * @param assetClass      the class of the data asset
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @return an unloaded asset manager
     */
    <T extends DataAsset> AssetManager<T> unloadedAssetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger);

    /**
     * Creates an asset manager for the specified asset class and parent directory.
     * Automatically reloads the asset manager.
     *
     * @param <T>             the type of data asset
     * @param assetClass      the class of the data asset
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @return a loaded asset manager
     */
    <T extends DataAsset> AssetManager<T> assetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger);


    /**
     * Creates a generator manager for the specified generator class and parent directory.
     * Needs to be reloaded manually.
     * Useful in case of looking for instantiation while delaying assets loading.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @return an unloaded generator manager
     */
    <T extends DataAsset> GeneratorManager<T> unloadedGeneratorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger);

    /**
     * Creates a generator manager for the specified generator class and parent directory.
     * Automatically reloads the generator manager.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @return a loaded generator manager
     */
    <T extends DataAsset> GeneratorManager<T> generatorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger);


}
