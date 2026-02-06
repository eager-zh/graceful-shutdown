/**
 * Simple JUnit framework for JCStress-based testing of <i>locking by value</i> classes:
 * {@link com.github.valuelock.ReentrantValueLock ReentrantValueLock} and 
 * {@link com.github.valuelock.ReentrantReadWriteValueLock ReentrantReadWriteValueLock}.
 * It allows to run a class, annotated with {@link org.openjdk.jcstress.annotations.JCStressTest JCStressTest}
 * as JUnit test.
 * 
 * <p/>{@code try-finally} blocks are unnecessary in most if not all cases,
 * as no code inside of these blocks is supposed to cause an exception,
 * that could be treated anyhow else but a complete test failure, 
 * but included to enforce best coding practices.
  */
package com.github.valuelock.test.jcstress.infra;