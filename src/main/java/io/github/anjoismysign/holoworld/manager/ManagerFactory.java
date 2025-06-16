package io.github.anjoismysign.holoworld.manager;

import io.github.anjoismysign.holoworld.asset.AssetGenerator;
import io.github.anjoismysign.holoworld.asset.DataAsset;
import io.github.anjoismysign.holoworld.asset.IdentityGenerator;
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
     * @param failOnNullField if true, the manager will fail to load if any field is null
     * @return an unloaded asset manager
     */
    <T extends DataAsset> AssetManager<T> unloadedAssetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField);

    /**
     * Creates an asset manager for the specified asset class and parent directory.
     * Automatically reloads the asset manager.
     *
     * @param <T>             the type of data asset
     * @param assetClass      the class of the data asset
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @param failOnNullField if true, the manager will fail to load if any field is null
     * @return a loaded asset manager
     */
    <T extends DataAsset> AssetManager<T> assetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField);


    /**
     * Creates a generator manager for the specified generator class and parent directory.
     * Needs to be reloaded manually.
     * Useful in case of looking for instantiation while delaying assets loading.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @param failOnNullField if true, the manager will fail to load if any field is null
     * @return an unloaded generator manager
     */
    <T extends DataAsset> GeneratorManager<T> unloadedGeneratorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField);

    /**
     * Creates a generator manager for the specified generator class and parent directory.
     * Automatically reloads the generator manager.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @param failOnNullField if true, the manager will fail to load if any field is null
     * @return a loaded generator manager
     */
    <T extends DataAsset> GeneratorManager<T> generatorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField);

    /**
     * Creates an identity manager for the specified generator class and parent directory.
     * Needs to be reloaded manually.
     * Useful in case of looking for instantiation while delaying assets loading.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @param failOnNullField if true, the manager will fail to load if any field is null
     * @return an unloaded identity manager
     */
    <T extends DataAsset> IdentityManager<T> unloadedIdentityManager(
            @NotNull Class<? extends IdentityGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField);

    /**
     * Creates an identity manager for the specified generator class and parent directory.
     * Automatically reloads the identity manager.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @param failOnNullField if true, the manager will fail to load if any field is null
     * @return a loaded identity manager
     */
    <T extends DataAsset> IdentityManager<T> identityManager(
            @NotNull Class<? extends IdentityGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField);


}
