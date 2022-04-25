package com.github.xiaobo9.commons.exception;

/**
 * entity not found
 */
public class EntityNotFoundEx extends RuntimeException {
    public EntityNotFoundEx() {
        super("entity not found");
    }

    public EntityNotFoundEx(String message) {
        super(message);
    }

    public static EntityNotFoundEx of(Class<?> clazz) {
        return new EntityNotFoundEx(clazz.getName() + " not found");
    }
}
