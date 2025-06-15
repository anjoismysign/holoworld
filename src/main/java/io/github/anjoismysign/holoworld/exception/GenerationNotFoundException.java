package io.github.anjoismysign.holoworld.exception;

public class GenerationNotFoundException extends NullPointerException {

    public GenerationNotFoundException(String detail) {
        super(detail);
    }

    public GenerationNotFoundException(String identifier,
                                       Class<?> generatorClass) {
        this("'" + identifier + "' is not a valid " + generatorClass.getCanonicalName());
    }
}
