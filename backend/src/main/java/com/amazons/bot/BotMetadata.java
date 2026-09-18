package com.amazons.bot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a bot's identity for the registry/UI. Adding a bot to the app is:
 * implement {@link Bot}, annotate the class with this, register it as a Spring
 * bean (a plain {@code @Component} is enough) - nothing else to wire.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BotMetadata {

    String id();

    String displayName();

    /** 1 (trivial) through 10 (strongest) - used only for UI ordering/display. */
    int difficulty();

    String description() default "";
}
