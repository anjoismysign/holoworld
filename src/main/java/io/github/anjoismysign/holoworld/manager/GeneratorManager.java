package io.github.anjoismysign.holoworld.manager;

import io.github.anjoismysign.holoworld.asset.AssetGenerator;
import io.github.anjoismysign.holoworld.asset.DataAsset;
import io.github.anjoismysign.holoworld.asset.DataAssetEntry;
import io.github.anjoismysign.holoworld.exception.GenerationNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface GeneratorManager<T extends DataAsset> extends Manager, Iterable<T> {

    @NotNull
    Class<? extends AssetGenerator<T>> generatorClass();

    @NotNull
    File directory();

    @Nullable
    DataAssetEntry<T> fetchGeneration(@NotNull String identifier);

    @NotNull
    default DataAssetEntry<T> getGeneration(@NotNull String identifier) {
        DataAssetEntry<T> entry = fetchGeneration(identifier);
        if (entry == null)
            throw new GenerationNotFoundException(identifier, generatorClass());
        return entry;
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

    default Map<String, T> map() {
        return stream()
                .map(asset -> Map.entry(asset.identifier(), asset))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
