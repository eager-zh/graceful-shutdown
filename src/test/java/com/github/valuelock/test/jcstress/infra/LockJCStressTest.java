package com.github.valuelock.test.jcstress.infra;

import static com.github.valuelock.test.jcstress.infra.MustHappenOutcomeAnalyzer.MUST_HAPPEN_OUTCOME;

import java.lang.reflect.Method;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import org.junit.Assert;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.infra.results.ZZZ_Result;
import org.openjdk.jcstress.infra.results.ZZ_Result;
import org.openjdk.jcstress.infra.results.Z_Result;

/**
 * A JUnit wrapper around JCStress test, which deals with various {@link Lock}
 * implementations. Additionally it supports a determination of concurrent
 * acquiring of a lock: a JCStress test which intends to test concurrent
 * acquiring should use {@link ConcurrentAcquirer} annotation to tag its certain
 * methods or all methods, annotated with {@link Actor}. These methods should
 * call one of the {@link #awaitSharedAccess} methods, passing an instance of
 * one of the ZXXX_Result classes. The {@link #awaitSharedAccess} methods will
 * wait until all sharing parties will call them, and when this happens will
 * store {@code true} to {@code r1} field of ZXXX_Result instance, which could
 * be used in the analysis of test results by the means of
 * {@link org.openjdk.jcstress.annotations.Outcome Outcome} annotation.
 * 
 * @see ConcurrentAcquirer
 * @see Z_Result
 * @see ZZ_Result
 * @see ZZZ_Result
 */
public abstract class LockJCStressTest extends JUnitJCStressTest {

	protected final static String VALUE = "Value";

	protected static final String ANOTHER_VALUE = "AnotherValue";

	private static final int SHARED_ACCESS_AWAITING_PERIOD = 5;

	private final CyclicBarrier barrier;

	protected LockJCStressTest() {
		int numberOfSharingParties = determineNumberOfSharingParties();
		barrier = numberOfSharingParties > 0 ? new CyclicBarrier(numberOfSharingParties) : null;
		registerOutcomeAnalyzer(MUST_HAPPEN_OUTCOME, new MustHappenOutcomeAnalyzer());
	}

	protected int determineNumberOfSharingParties() {
		int numberOfSharingParties = 0;
		for (Class<?> clazz = getClass(); clazz != null; clazz = clazz.getSuperclass()) {
			if (clazz.isAnnotationPresent(ConcurrentAcquirer.class)) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Actor.class)) {
						numberOfSharingParties++;
					}
				}
			} else {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(ConcurrentAcquirer.class)) {
						if (!method.isAnnotationPresent(Actor.class)) {
							throw new IllegalStateException("Method " + method + " annotated with "
									+ ConcurrentAcquirer.class.getSimpleName() + " must be annotated with @Actor");
						}
						numberOfSharingParties++;
					}
				}
			}
		}
		return numberOfSharingParties;
	}

	protected void lock(Lock lock) {
		lock.lock();
	}

	protected boolean tryLock(Lock lock) {
		return lock.tryLock();
	}

	protected boolean tryLock(Lock lock, long time, TimeUnit unit) throws InterruptedException {
		return lock.tryLock(time, unit);
	}

	protected void lockInterruptibly(Lock lock) throws InterruptedException {
		lock.lockInterruptibly();
	}

	protected void unlock(Lock lock) {
		lock.unlock();
	}

	/**
	 * Waits until all other sharing parties call this method. When this happens,
	 * the method stores {@code true} to the {@link Z_Result#r1} field of the
	 * {@code r} parameter.
	 */
	protected void awaitSharedAccess(Z_Result r) {
		awaitSharedAccess(b -> r.r1 = b);
	}

	/**
	 * Waits until all other sharing parties call this method. When this happens,
	 * the method stores {@code true} to the {@link ZZ_Result#r1} field of the
	 * {@code r} parameter.
	 */
	protected void awaitSharedAccess(ZZ_Result r) {
		awaitSharedAccess(b -> r.r1 = b);
	}

	/**
	 * Waits until all other sharing parties call this method. When this happens,
	 * the method stores {@code true} to the {@link ZZZ_Result#r1} field of the
	 * {@code r} parameter.
	 */
	protected void awaitSharedAccess(ZZZ_Result r) {
		awaitSharedAccess(b -> r.r1 = b);
	}

	protected void awaitSharedAccess(Consumer<Boolean> resultSetter) {
		if (barrier == null) {
			throw new IllegalStateException("No sharing parties were declared");
		}
		try {
			if (0 == barrier.await(SHARED_ACCESS_AWAITING_PERIOD, TimeUnit.SECONDS)) {
				resultSetter.accept(true);
			}
		} catch (TimeoutException e) {
			Assert.fail("Test hangs");
		} catch (InterruptedException | BrokenBarrierException e) {
			throw new RuntimeException(e);
		}
	}

}
