package io.github.anjoismysign.holoworld.manager;

import io.github.anjoismysign.aesthetic.DirectoryAssistant;
import io.github.anjoismysign.holoworld.asset.AssetGenerator;
import io.github.anjoismysign.holoworld.asset.DataAsset;
import io.github.anjoismysign.holoworld.asset.DataAssetEntry;
import io.github.anjoismysign.holoworld.asset.IdentityGeneration;
import io.github.anjoismysign.holoworld.asset.IdentityGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    public <T extends DataAsset> AssetManager<T> unloadedAssetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField) {
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

            String path = file.getPath();
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                Constructor constructor = new Constructor(assetClass, new LoaderOptions());
                constructor.getPropertyUtils().setSkipMissingProperties(!failOnNullField);
                Yaml yaml = new Yaml(constructor);
                T instance = yaml.load(fileInputStream);
                Objects.requireNonNull(instance.identifier(), path + " attempted to be read, but 'identifier' cannot be null");
                return instance;
            } catch (Throwable throwable) {
                throw new RuntimeException("Found the following issue at '" + path + "'\n" + toStackTrace(throwable));
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
                    logger.severe(assetClass().getCanonicalName() + " has duplicates for'" + key + "' : " + duplicates);
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
                String path = file.getPath();

                Representer representer = new Representer(new DumperOptions());
                representer.addClassTag(assetClass, Tag.MAP);
                Yaml yaml = new Yaml(representer);
                try (FileWriter writer = new FileWriter(file.getAbsolutePath())) {
                    yaml.dump(element, writer);
                } catch (Throwable throwable) {
                    throw new RuntimeException("Found the following issue at '" + path + "'\n" + toStackTrace(throwable));
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

    public <T extends DataAsset> AssetManager<T> assetManager(
            @NotNull Class<T> assetClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField) {
        AssetManager<T> unloaded = unloadedAssetManager(
                assetClass,
                parentDirectory,
                logger,
                failOnNullField);
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
            @Nullable Logger logger,
            boolean failOnNullField) {
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
            String path = file.getPath();
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                Constructor constructor = new Constructor(generatorClass, new LoaderOptions());
                constructor.getPropertyUtils().setSkipMissingProperties(!failOnNullField);
                Yaml yaml = new Yaml(constructor);
                AssetGenerator<T> instance = yaml.load(fileInputStream);
                Objects.requireNonNull(instance.identifier(), path + " attempted to be read, but 'identifier' cannot be null");
                return instance;
            } catch (Throwable throwable) {
                throw new RuntimeException("Found the following issue at '" + path + "'\n" + toStackTrace(throwable));
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
                String path = file.getPath();

                Representer representer = new Representer(new DumperOptions());
                representer.addClassTag(generatorClass, Tag.MAP);
                Yaml yaml = new Yaml(representer);
                try (FileWriter writer = new FileWriter(file.getAbsolutePath())) {
                    yaml.dump(element, writer);
                } catch (Throwable throwable) {
                    throw new RuntimeException("Found the following issue at '" + path + "'\n" + toStackTrace(throwable));
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
                    logger.severe(generatorClass.getCanonicalName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
                assets.clear();
            }
        };
    }

    public <T extends DataAsset> GeneratorManager<T> generatorManager(
            @NotNull Class<? extends AssetGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField) {
        GeneratorManager<T> unloaded = unloadedGeneratorManager(
                generatorClass,
                parentDirectory,
                logger,
                failOnNullField);
        unloaded.reload();
        return unloaded;
    }

    public <T extends DataAsset> IdentityManager<T> unloadedIdentityManager(
            @NotNull Class<? extends IdentityGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField) {
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
            String path = file.getPath();
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                Constructor constructor = new Constructor(generatorClass, new LoaderOptions());
                constructor.getPropertyUtils().setSkipMissingProperties(!failOnNullField);
                Yaml yaml = new Yaml(constructor);
                IdentityGenerator<T> instance = yaml.load(fileInputStream);
                Objects.requireNonNull(identifier, path + " attempted to be read, but 'identifier' cannot be null");
                return new IdentityGeneration<>(identifier, instance);
            } catch (Throwable throwable) {
                throw new RuntimeException("Found the following issue at '" + path + "'\n" + toStackTrace(throwable));
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
                String path = file.getPath();

                Representer representer = new Representer(new DumperOptions());
                representer.addClassTag(generatorClass, Tag.MAP);
                Yaml yaml = new Yaml(representer);
                try (FileWriter writer = new FileWriter(file.getAbsolutePath())) {
                    yaml.dump(element.generator(), writer);
                } catch (Throwable throwable) {
                    throw new RuntimeException("Found the following issue at '" + path + "'\n" + toStackTrace(throwable));
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
                    logger.severe(generatorClass.getCanonicalName() + " has duplicates for'" + key + "' : " + duplicates);
                }));
                duplicates.clear();
                assets.clear();
            }
        };
    }

    public <T extends DataAsset> IdentityManager<T> identityManager(
            @NotNull Class<? extends IdentityGenerator<T>> generatorClass,
            @NotNull File parentDirectory,
            @Nullable Logger logger,
            boolean failOnNullField) {
        IdentityManager<T> unloaded = unloadedIdentityManager(
                generatorClass,
                parentDirectory,
                logger,
                failOnNullField);
        unloaded.reload();
        return unloaded;
    }

    private String toStackTrace(@NotNull Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }

}
