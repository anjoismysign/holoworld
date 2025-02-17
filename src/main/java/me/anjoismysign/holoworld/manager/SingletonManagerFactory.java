package me.anjoismysign.holoworld.manager;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.anjoismysign.aesthetic.DirectoryAssistant;
import me.anjoismysign.holoworld.asset.AssetGenerator;
import me.anjoismysign.holoworld.asset.DataAsset;
import me.anjoismysign.holoworld.asset.DataAssetEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
        final Map<String, Map<String, DataAssetEntry<T>>> assetsLocales = new HashMap<>();
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
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
                String locale = asset.locale();
                if (logger != null)
                    logger.info(identifier + ":" + locale);
                locale = locale == null ? defaultLocale() : locale;
                if (logger != null)
                    logger.info("getting locale '" + locale + "' for " + path);
                Map<String, DataAssetEntry<T>> assets = assetsLocales.computeIfAbsent(locale, (key) -> new HashMap<>());
                if (logger != null)
                    logger.info("loaded : " + asset.identifier() + ":" + locale + " for " + path);
                DataAssetEntry<T> entry = parse.apply(file, asset);
                @Nullable DataAssetEntry<T> previous = assets.put(identifier, entry);
                if (previous == null)
                    return;
                List<String> list = duplicates.computeIfAbsent(identifier, k -> new ArrayList<>());
                list.add(previous.file().getAbsolutePath());
                list.add(file.getAbsolutePath());
            });

            assetsLocales.forEach((locale, assets) -> {
                if (logger != null)
                    logger.info("loaded locale (" + locale + ") with identifiers: [" + String.join(",", assets.keySet()) + "]");
            });
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
            public @NotNull String defaultLocale() {
                return SingletonManagerFactory.this.defaultLocale();
            }

            @Override
            public void reload() {
                assetsLocales.clear();
                readAll.run();
                duplicates.forEach((key, list) -> ifLogger(logger -> {
                    String duplicates = "{" + String.join(", ", list) + "}";
                    logger.severe(assetClass().getSimpleName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
            }

            @Override
            public @Nullable DataAssetEntry<T> fetchAssetByLocale(@NotNull String identifier,
                                                                  @NotNull String locale) {
                @Nullable Map<String, DataAssetEntry<T>> lookUp = assetsLocales.get(locale);
                Map<String, DataAssetEntry<T>> assets = lookUp == null ? Objects.requireNonNull(assetsLocales.get(defaultLocale()), fallbackMessage(assetClass, parentDirectory)) : lookUp;
                return assets.get(identifier);
            }

            @Override
            public @NotNull Set<String> getIdentifiers() {
                Set<String> identifiers = new HashSet<>();
                assetsLocales.forEach(((locale, stringDataAssetEntryMap) -> {
                    if (logger != null)
                        logger.info("getting identifiers for locale: " + locale);
                    identifiers.addAll(stringDataAssetEntryMap.keySet());
                }));
                return identifiers;
            }


            @Override
            public boolean add(@NotNull T element) {
                Objects.requireNonNull(element, "'element' cannot be null");
                String identifier = element.identifier();
                @Nullable String elementLocale = element.locale();
                String locale = elementLocale == null ? defaultLocale() : elementLocale;
                File file = new File(parentDirectory, identifier);
                String path = file.getPath();
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                try {
                    mapper.writeValue(file, element);
                } catch (IOException exception) {
                    exception.printStackTrace();
                    return false;
                }

                Map<String, DataAssetEntry<T>> assets = assetsLocales.computeIfAbsent(locale, (key) -> new HashMap<>());
                if (logger != null)
                    logger.info("loaded : " + element.identifier() + ":" + locale + " for " + path);
                DataAssetEntry<T> entry = parse.apply(file, element);
                DataAssetEntry<T> previous = assets.put(identifier, entry);
                if (previous != null) {
                    T previousAsset = previous.asset();
                    String previousPath = previous.file().getPath();
                    T currentAsset = entry.asset();
                    String currentPath = entry.file().getPath();

                    System.out.println(previousPath + " and " + previousAsset.identifier() + ":" + previousAsset.locale() + " was replaced by " + currentPath + " (" + currentAsset.identifier() + ":" + currentAsset.locale() + ")");
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
        final Map<String, Map<String, DataAssetEntry<? extends AssetGenerator<T>>>> assetsLocales = new HashMap<>();
        final Map<String, Map<String, DataAssetEntry<T>>> generationsLocales = new HashMap<>();
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
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
                    @Nullable String locale = assetGenerator.locale();
                    if (logger != null)
                        logger.info(identifier + ":" + locale);
                    locale = locale == null ? defaultLocale() : locale;
                    if (logger != null)
                        logger.info("getting locale '" + locale + "' for " + path);
                    Map<String, DataAssetEntry<? extends AssetGenerator<T>>> assets = assetsLocales.computeIfAbsent(locale, (key) -> new HashMap<>());
                    if (logger != null)
                        logger.info("loaded : " + assetGenerator.identifier() + ":" + locale + " for " + path);
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
            assetsLocales.forEach((locale, assets) -> {
                try {
                    if (logger != null)
                        logger.info("loaded locale (" + locale + ") with identifiers: [" + String.join(",", assets.keySet()) + "]");

                    Map<String, DataAssetEntry<T>> generations = generationsLocales.computeIfAbsent(locale, (key) -> new HashMap<>());
                    assets.forEach((identifier, generatorEntry) -> {
                        File file = generatorEntry.file();
                        T asset = generatorEntry.asset().generate();
                        if (logger != null) {
                            if (asset == null)
                                logger.severe("asset is null: " + file.getPath());
                            else
                                logger.info("loaded generation: " + asset.identifier() + ":" + locale);
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
                            logger.info("loaded DataAssetEntry: " + asset.identifier() + ":" + locale);

                        generations.put(identifier, assetEntry);
                    });

                    if (logger != null)
                        logger.info("loaded locale (" + locale + ") with generations: " + String.join(",", generations.keySet()));
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            });
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
            public @NotNull String defaultLocale() {
                return SingletonManagerFactory.this.defaultLocale();
            }

            @Override
            public @Nullable DataAssetEntry<T> fetchGenerationByLocale(@NotNull String identifier,
                                                                       @NotNull String locale) {
                @Nullable Map<String, DataAssetEntry<T>> lookUp = generationsLocales.get(locale);
                Map<String, DataAssetEntry<T>> generations = lookUp == null ? Objects.requireNonNull(generationsLocales.get(defaultLocale()), fallbackMessage(generatorClass, parentDirectory)) : lookUp;
                return generations.get(identifier);
            }


            @Override
            public @NotNull Set<String> getIdentifiers() {
                Set<String> identifiers = new HashSet<>();
                generationsLocales.forEach(((locale, stringDataAssetEntryMap) -> {
                    if (logger != null)
                        logger.info("getting identifiers for locale: " + locale);
                    identifiers.addAll(stringDataAssetEntryMap.keySet());
                }));
                return identifiers;
            }

            @Override
            public boolean add(@NotNull AssetGenerator<T> element) {
                Objects.requireNonNull(element, "'element' cannot be null");
                String identifier = element.identifier();
                @Nullable String elementLocale = element.locale();
                String locale = elementLocale == null ? defaultLocale() : elementLocale;
                File file = new File(parentDirectory, identifier);
                String path = file.getPath();
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                try {
                    mapper.writeValue(file, element);
                } catch (IOException exception) {
                    exception.printStackTrace();
                    return false;
                }

                Map<String, DataAssetEntry<? extends AssetGenerator<T>>> assets = assetsLocales.computeIfAbsent(locale, (key) -> new HashMap<>());
                if (logger != null)
                    logger.info("loaded : " + element.identifier() + ":" + locale + " for " + path);
                DataAssetEntry<? extends AssetGenerator<T>> entry = parse.apply(file, element);
                DataAssetEntry<? extends AssetGenerator<T>> previous = assets.put(identifier, entry);
                if (previous != null) {
                    AssetGenerator<T> previousGenerator = previous.asset();
                    String previousPath = previous.file().getPath();
                    AssetGenerator<T> currentGenerator = entry.asset();
                    String currentPath = entry.file().getPath();

                    System.out.println(previousPath + " and " + previousGenerator.identifier() + ":" + previousGenerator.locale() + " was replaced by " + currentPath + " (" + currentGenerator.identifier() + ":" + currentGenerator.locale() + ")");
                }
                return true;
            }

            @Override
            public @Nullable Logger logger() {
                return logger;
            }

            @Override
            public void reload() {
                generationsLocales.clear();
                readAll.run();
                duplicates.forEach((key, list) -> ifLogger(logger -> {
                    String duplicates = "{" + String.join(", ", list) + "}";
                    logger.severe(generatorClass.getSimpleName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
                assetsLocales.clear();
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
}
