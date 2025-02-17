package me.anjoismysign.holoworld.manager;

import me.anjoismysign.holoworld.asset.AssetGenerator;
import me.anjoismysign.holoworld.asset.DataAsset;
import me.anjoismysign.holoworld.asset.DataAssetEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface GeneratorManager<T extends DataAsset> extends Manager, Iterable<T> {

    @NotNull
    Class<? extends AssetGenerator<T>> generatorClass();

    @NotNull
    File directory();

    @NotNull
    String defaultLocale();

    @Nullable
    DataAssetEntry<T> fetchGenerationByLocale(@NotNull String identifier,
                                              @NotNull String locale);

    @Nullable
    default DataAssetEntry<T> fetchGeneration(@NotNull String identifier) {
        return fetchGenerationByLocale(identifier, defaultLocale());
    }

    @NotNull
    Set<String> getIdentifiers();

    boolean add(@NotNull AssetGenerator<T> element);

    default int size() {
        return getIdentifiers().size();
    }

    default boolean isEmpty() {
        return getIdentifiers().isEmpty();
    }

    default Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    default Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    @Override
    default Iterator<T> iterator() {
        // The iterator goes over the identifiers and returns the asset (via fetchAsset)
        return new Iterator<>() {
            private final Iterator<String> idIterator = getIdentifiers().iterator();

            @Override
            public boolean hasNext() {
                return idIterator.hasNext();
            }

            @Override
            public T next() {
                String id = idIterator.next();
                DataAssetEntry<T> entry = fetchGeneration(id);
                return Objects.requireNonNull(entry, "entry is null").asset();
            }
        };
    }

}
