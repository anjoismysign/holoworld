package me.anjoismysign.holoworld.manager;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.anjoismysign.aesthetic.DirectoryAssistant;
import me.anjoismysign.holoworld.asset.AssetGenerator;
import me.anjoismysign.holoworld.asset.DataAsset;
import me.anjoismysign.holoworld.asset.DataAssetEntry;
import me.anjoismysign.holoworld.asset.IdentityGeneration;
import me.anjoismysign.holoworld.asset.IdentityGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A factory class for creating managers for assets and generators.
 * Uses "en_us" as default Locale
 */
public enum SingletonManagerFactory implements ManagerFactory {
    INSTANCE;

    @Override
    public @NotNull String defaultLocale() {
        return "en_us";
    }

    private <T extends DataAsset> String fallbackMessage(@NotNull Class<T> assetClass,
                                                         @NotNull File parentDirectory) {
        return "Couldn't fallback to default locale: '" + defaultLocale() + "'\nAt: " + parentDirectory.getPath();
    }

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
    public <T extends DataAsset> AssetManager<T> unloadedAssetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger) {
        final Map<String, DataAssetEntry<T>> assets = new HashMap<>();
        final Map<String, List<String>> duplicates = new HashMap<>();

        //@Nullable output
        Function<File, T> read = file -> {
            Objects.requireNonNull(file, "'file' cannot be null");
            if (!file.isFile())
                return null;
            String content;
            try {
                content = new String(Files.readAllBytes(file.toPath()));
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }

            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
            String path = file.getPath();
            try {
                T instance = objectMapper.readValue(content, assetClass);
                Objects.requireNonNull(instance.identifier(), path + " attempted to be read, but 'identifier' cannot be null");
                return instance;
            } catch (Throwable throwable) {
                throw new RuntimeException("Found the following issue at '" + path + "'\n" + throwable.getMessage());
            }
        };

        BiFunction<File, T, DataAssetEntry<T>> parse = ((file, dataAsset) -> new DataAssetEntry<>() {
            public @NotNull File file() {
                return file;
            }

            public @NotNull T asset() {
                return dataAsset;
            }
        });

        Runnable readAll = () -> {
            if (!parentDirectory.exists()) {
                parentDirectory.mkdirs();
                return;
            }

            String extension = ".yml";

            DirectoryAssistant directoryAssistant = DirectoryAssistant.of(parentDirectory);
            Collection<File> files = directoryAssistant.listRecursively(extension);
            if (logger != null)
                logger.info(parentDirectory.getPath() + " has this many files (" + files.size() + ")");
            files.forEach(file -> {
                String path = file.getPath();
                if (logger != null)
                    logger.info(path);
                @Nullable T asset = read.apply(file);
                if (asset == null)
                    return;
                String identifier = asset.identifier();
                DataAssetEntry<T> entry = parse.apply(file, asset);
                @Nullable DataAssetEntry<T> previous = assets.put(identifier, entry);
                if (previous == null)
                    return;
                List<String> list = duplicates.computeIfAbsent(identifier, k -> new ArrayList<>());
                list.add(previous.file().getAbsolutePath());
                list.add(file.getAbsolutePath());
            });


            if (logger != null)
                logger.info("loaded with identifiers: [" + String.join(",", assets.keySet()) + "]");
        };

        return new AssetManager<>() {
            @Override
            public @Nullable Logger logger() {
                return logger;
            }

            @Override
            public @NotNull Class<T> assetClass() {
                return assetClass;
            }

            @Override
            public @NotNull File directory() {
                return parentDirectory;
            }

            @Override
            public void reload() {
                assets.clear();
                readAll.run();
                duplicates.forEach((key, list) -> ifLogger(logger -> {
                    String duplicates = "{" + String.join(", ", list) + "}";
                    logger.severe(assetClass().getSimpleName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
            }

            @Override
            public @Nullable DataAssetEntry<T> fetchAsset(@NotNull String identifier) {
                return assets.get(identifier);
            }

            @Override
            public @NotNull Set<String> getIdentifiers() {
                return assets.keySet();
            }


            @Override
            public boolean add(@NotNull T element) {
                Objects.requireNonNull(element, "'element' cannot be null");
                String identifier = element.identifier();
                File file = new File(parentDirectory, identifier + ".yml");
                ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
                try {
                    objectMapper.writeValue(file, element);
                } catch (IOException exception) {
                    exception.printStackTrace();
                    return false;
                }

                DataAssetEntry<T> entry = parse.apply(file, element);
                DataAssetEntry<T> previous = assets.put(identifier, entry);
                if (previous != null) {
                    T previousAsset = previous.asset();
                    String previousPath = previous.file().getPath();
                    T currentAsset = entry.asset();
                    String currentPath = entry.file().getPath();

                    System.out.println(previousPath + " and " + previousAsset.identifier() + " was replaced by " + currentPath + " (" + currentAsset.identifier() + ")");
                }
                return true;
            }
        };
    }

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
    public <T extends DataAsset> AssetManager<T> assetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger) {
        AssetManager<T> unloaded = unloadedAssetManager(assetClass, parentDirectory, logger);
        unloaded.reload();
        return unloaded;
    }

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
    public <T extends DataAsset> GeneratorManager<T> unloadedGeneratorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger) {
        final Map<String, DataAssetEntry<? extends AssetGenerator<T>>> assets = new HashMap<>();
        final Map<String, DataAssetEntry<T>> generations = new HashMap<>();
        final Map<String, List<String>> duplicates = new HashMap<>();

        //@Nullable output
        Function<File, ? extends AssetGenerator<T>> read = file -> {
            Objects.requireNonNull(file, "'file' cannot be null");
            if (!file.isFile())
                return null;
            String content;
            try {
                content = new String(Files.readAllBytes(file.toPath()));
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
            String path = file.getPath();
            try {
                AssetGenerator<T> instance = objectMapper.readValue(content, generatorClass);
                Objects.requireNonNull(instance.identifier(), path + " attempted to be read, but 'identifier' cannot be null");
                return instance;
            } catch (Throwable throwable) {
                throw new RuntimeException("Found the following issue at '" + path + "'\n" + throwable.getMessage());
            }
        };

        BiFunction<File, AssetGenerator<T>, DataAssetEntry<AssetGenerator<T>>> parse = ((file, dataAsset) -> new DataAssetEntry<>() {
            public @NotNull File file() {
                return file;
            }

            public @NotNull AssetGenerator<T> asset() {
                return dataAsset;
            }
        });

        Runnable readAll = () -> {
            if (!parentDirectory.exists()) {
                parentDirectory.mkdirs();
                return;
            }

            String extension = ".yml";

            DirectoryAssistant directoryAssistant = DirectoryAssistant.of(parentDirectory);
            Collection<File> files = directoryAssistant.listRecursively(extension);
            if (logger != null)
                logger.info(parentDirectory.getPath() + " has this many files (" + files.size() + ")");
            files.forEach(file -> {
                try {
                    String path = file.getPath();
                    if (logger != null)
                        logger.info(path);
                    @Nullable AssetGenerator<T> assetGenerator = read.apply(file);
                    if (assetGenerator == null)
                        return;
                    String identifier = assetGenerator.identifier();
                    DataAssetEntry<? extends AssetGenerator<T>> entry = parse.apply(file, assetGenerator);
                    @Nullable DataAssetEntry<? extends AssetGenerator<T>> previous = assets.put(identifier, entry);
                    if (previous == null)
                        return;
                    List<String> list = duplicates.computeIfAbsent(identifier, k -> new ArrayList<>());
                    list.add(previous.file().getAbsolutePath());
                    list.add(file.getAbsolutePath());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            });

            try {
                if (logger != null)
                    logger.info("loaded with identifiers: [" + String.join(",", assets.keySet()) + "]");

                assets.forEach((identifier, generatorEntry) -> {
                    File file = generatorEntry.file();
                    T asset = generatorEntry.asset().generate();
                    if (logger != null) {
                        logger.info("loaded generation: " + asset.identifier());
                    }
                    DataAssetEntry<T> assetEntry = new DataAssetEntry<>() {
                        @Override
                        public @NotNull File file() {
                            return file;
                        }

                        @Override
                        public @NotNull T asset() {
                            return asset;
                        }
                    };
                    if (logger != null)
                        logger.info("loaded DataAssetEntry: " + asset.identifier());

                    generations.put(identifier, assetEntry);
                });

                if (logger != null)
                    logger.info("loaded with generations: " + String.join(",", generations.keySet()));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        };

        return new GeneratorManager<>() {
            @Override
            public @NotNull Class<? extends AssetGenerator<T>> generatorClass() {
                return generatorClass;
            }

            @Override
            public @NotNull File directory() {
                return parentDirectory;
            }

            @Override
            public @Nullable DataAssetEntry<T> fetchGeneration(@NotNull String identifier) {
                return generations.get(identifier);
            }


            @Override
            public @NotNull Set<String> getIdentifiers() {
                return generations.keySet();
            }

            @Override
            public boolean add(@NotNull AssetGenerator<T> element) {
                Objects.requireNonNull(element, "'element' cannot be null");
                String identifier = element.identifier();
                File file = new File(parentDirectory, identifier + ".yml");
                ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
                try {
                    objectMapper.writeValue(file, element);
                } catch (IOException exception) {
                    exception.printStackTrace();
                    return false;
                }

                DataAssetEntry<? extends AssetGenerator<T>> entry = parse.apply(file, element);
                DataAssetEntry<? extends AssetGenerator<T>> previous = assets.put(identifier, entry);
                if (previous != null) {
                    AssetGenerator<T> previousGenerator = previous.asset();
                    String previousPath = previous.file().getPath();
                    AssetGenerator<T> currentGenerator = entry.asset();
                    String currentPath = entry.file().getPath();

                    System.out.println(previousPath + " and " + previousGenerator.identifier() + " was replaced by " + currentPath + " (" + currentGenerator.identifier() + ")");
                }
                return true;
            }

            @Override
            public @Nullable Logger logger() {
                return logger;
            }

            @Override
            public void reload() {
                generations.clear();
                readAll.run();
                duplicates.forEach((key, list) -> ifLogger(logger -> {
                    String duplicates = "{" + String.join(", ", list) + "}";
                    logger.severe(generatorClass.getSimpleName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
                assets.clear();
            }
        };
    }

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
    public <T extends DataAsset> GeneratorManager<T> generatorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger) {
        GeneratorManager<T> unloaded = unloadedGeneratorManager(generatorClass, parentDirectory, logger);
        unloaded.reload();
        return unloaded;
    }

    /**
     * Creates an identity manager for the specified generator class and parent directory.
     * Needs to be reloaded manually.
     * Useful in case of looking for instantiation while delaying assets loading.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @return an unloaded identity manager
     */
    public <T extends DataAsset> IdentityManager<T> unloadedIdentityManager(
            @NotNull Class<? extends IdentityGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger) {
        final Map<String, DataAssetEntry<? extends IdentityGeneration<T>>> assets = new HashMap<>();
        final Map<String, DataAssetEntry<T>> generations = new HashMap<>();
        final Map<String, List<String>> duplicates = new HashMap<>();

        //@Nullable output
        Function<File, IdentityGeneration<T>> read = file -> {
            Objects.requireNonNull(file, "'file' cannot be null");
            if (!file.isFile())
                return null;
            String identifier = file.getName().replace(".yml", "");
            String content;
            try {
                content = new String(Files.readAllBytes(file.toPath()));
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
            String path = file.getPath();
            try {
                IdentityGenerator<T> instance = objectMapper.readValue(content, generatorClass);
                Objects.requireNonNull(identifier, path + " attempted to be read, but 'identifier' cannot be null");
                return new IdentityGeneration<>(identifier, instance);
            } catch (Throwable throwable) {
                throw new RuntimeException("Found the following issue at '" + path + "'\n" + throwable.getMessage());
            }
        };

        BiFunction<File, IdentityGeneration<T>, DataAssetEntry<IdentityGeneration<T>>> parse = ((file, dataAsset) -> new DataAssetEntry<>() {
            public @NotNull File file() {
                return file;
            }

            public @NotNull IdentityGeneration<T> asset() {
                return dataAsset;
            }
        });

        Runnable readAll = () -> {
            if (!parentDirectory.exists()) {
                parentDirectory.mkdirs();
                return;
            }

            String extension = ".yml";

            DirectoryAssistant directoryAssistant = DirectoryAssistant.of(parentDirectory);
            Collection<File> files = directoryAssistant.listRecursively(extension);
            if (logger != null)
                logger.info(parentDirectory.getPath() + " has this many files (" + files.size() + ")");
            files.forEach(file -> {
                try {
                    String path = file.getPath();
                    if (logger != null)
                        logger.info(path);
                    @Nullable IdentityGeneration<T> identityGenerator = read.apply(file);
                    if (identityGenerator == null)
                        return;
                    String identifier = identityGenerator.identifier();
                    DataAssetEntry<? extends IdentityGeneration<T>> entry = parse.apply(file, identityGenerator);
                    @Nullable DataAssetEntry<? extends IdentityGeneration<T>> previous = assets.put(identifier, entry);
                    if (previous == null)
                        return;
                    List<String> list = duplicates.computeIfAbsent(identifier, k -> new ArrayList<>());
                    list.add(previous.file().getAbsolutePath());
                    list.add(file.getAbsolutePath());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            });

            try {
                if (logger != null)
                    logger.info("loaded with identifiers: [" + String.join(",", assets.keySet()) + "]");

                assets.forEach((identifier, generatorEntry) -> {
                    File file = generatorEntry.file();
                    IdentityGeneration<T> generation = generatorEntry.asset();
                    T asset = generation.asset();
                    if (logger != null) {
                        if (asset == null)
                            logger.severe("asset is null: " + file.getPath());
                        else
                            logger.info("loaded generation: " + asset.identifier());
                    }
                    DataAssetEntry<T> assetEntry = new DataAssetEntry<>() {
                        @Override
                        public @NotNull File file() {
                            return file;
                        }

                        @Override
                        public @NotNull T asset() {
                            return asset;
                        }
                    };
                    if (logger != null)
                        logger.info("loaded DataAssetEntry: " + asset.identifier());

                    generations.put(identifier, assetEntry);
                });

                if (logger != null)
                    logger.info("loaded with generations: " + String.join(",", generations.keySet()));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        };

        return new IdentityManager<>() {

            @Override
            public @NotNull Class<? extends IdentityGenerator<T>> generatorClass() {
                return generatorClass;
            }

            @Override
            public @NotNull File directory() {
                return parentDirectory;
            }

            @Override
            public @Nullable DataAssetEntry<T> fetchGeneration(@NotNull String identifier) {
                return generations.get(identifier);
            }

            @Override
            public @NotNull Set<String> getIdentifiers() {
                return generations.keySet();
            }

            @Override
            public boolean add(@NotNull IdentityGeneration<T> element) {
                Objects.requireNonNull(element, "'element' cannot be null");
                String identifier = element.identifier();
                File file = new File(parentDirectory, identifier + ".yml");
                ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
                try {
                    objectMapper.writeValue(file, element.generator());
                } catch (IOException exception) {
                    exception.printStackTrace();
                    return false;
                }

                DataAssetEntry<? extends IdentityGeneration<T>> entry = parse.apply(file, element);
                DataAssetEntry<? extends IdentityGeneration<T>> previous = assets.put(identifier, entry);
                if (previous != null) {
                    IdentityGeneration<T> previousGenerator = previous.asset();
                    String previousPath = previous.file().getPath();
                    IdentityGeneration<T> currentGenerator = entry.asset();
                    String currentPath = entry.file().getPath();

                    System.out.println(previousPath + " and " + previousGenerator.identifier() + " was replaced by " + currentPath + " (" + currentGenerator.identifier() + ")");
                }
                return true;
            }

            @Override
            public @Nullable Logger logger() {
                return logger;
            }

            @Override
            public void reload() {
                generations.clear();
                readAll.run();
                duplicates.forEach((key, list) -> ifLogger(logger -> {
                    String duplicates = "{" + String.join(", ", list) + "}";
                    logger.severe(generatorClass.getSimpleName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
                assets.clear();
            }
        };
    }

    /**
     * Creates an identity manager for the specified generator class and parent directory.
     * Automatically reloads the identity manager.
     *
     * @param <T>             the type of data asset
     * @param generatorClass  the class of the asset generator
     * @param parentDirectory the parent directory for the assets
     * @param logger          the logger to use for logging
     * @return a loaded identity manager
     */
    public <T extends DataAsset> IdentityManager<T> identityManager(
            @NotNull Class<? extends IdentityGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger) {
        IdentityManager<T> unloaded = unloadedIdentityManager(generatorClass, parentDirectory, logger);
        unloaded.reload();
        return unloaded;
    }

}
