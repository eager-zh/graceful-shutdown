package com.github.valuelock.test.jcstress.infra;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.openjdk.jcstress.annotations.Actor;

/**
 * Indicates that a method annotated with this annotation attempts to acquire a
 * lock simultaneously. If the annotation is applied to a test class, then it
 * indicates that all the methods of this class, annotated with {@link Actor},
 * attempt to acquire a lock simultaneously
 * 
 * @see LockJCStressTest
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface ConcurrentAcquirer {
}