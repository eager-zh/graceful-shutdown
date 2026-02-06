package com.github.valuelock.test.jcstress.infra;

import java.util.concurrent.TimeoutException;

/**
 * An executor of JCStress test. Intended for debug purposes only.
 */
public interface TestRunner {

	/**
	 * @param test instance of JCStress test, a class annotated with
	 *             {@link org.openjdk.jcstress.annotations.JCStressTest
	 *             JCStressTest}.
	 * @return any object describing a result of JCStress test
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	Object runTest(Object test) throws InterruptedException, TimeoutException;

}
