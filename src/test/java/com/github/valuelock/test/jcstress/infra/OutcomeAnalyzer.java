package com.github.valuelock.test.jcstress.infra;

import org.openjdk.jcstress.util.Counter;

/**
 * Implementation of this interface may make additional checks of JCStress'
 * {@link org.openjdk.jcstress.infra.collectors.TestResult TestResult}, and may
 * not like it despite that the JCStress itself found
 * nothing wrong in this result. If an OutcomeAnalyzer does find something
 * wrong there, it might throw {@link AssertionError}.
 * <p/>
 * OutcomeAnalyzer might be added to {@link JUnitJCStressTest} instance by
 * {@link JUnitJCStressTest#registerOutcomeAnalyzer(String, OutcomeAnalyzer)
 * registerOutcomeAnalyzer} method and {@link JUnitJCStressTest} will invoke its
 * {@link #analyze(String, Counter) analyze} method for each
 * {@link org.openjdk.jcstress.annotations.Outcome @Outcome} in a
 * {@link org.openjdk.jcstress.infra.collectors.TestResult TestResult} in its
 * {@link JUnitJCStressTest#analyzeTestResults(java.util.Map, java.util.Collection)
 * analyzeTestResults} method. 
 *
 * @see MustHappenOutcomeAnalyzer
 * @see JUnitJCStressTest
 * 
 * @param <R> type of result
 */
public interface OutcomeAnalyzer<R> {

	/**
	 * Invoked by {@link JUnitJCStressTest} for every
	 * {@link org.openjdk.jcstress.annotations.Outcome @Outcome} of every
	 * {@link org.openjdk.jcstress.infra.collectors.TestResult TestResult} for every
	 * OutcomeAnalyzer, registered by
	 * {@link JUnitJCStressTest#registerOutcomeAnalyzer(String, OutcomeAnalyzer)
	 * registerOutcomeAnalyzer} method
	 * 
	 * @param outcomeDesc {@link org.openjdk.jcstress.annotations.Outcome#desc()
	 *                    desc} attribute of an Outcome
	 * @param counter     counter value of this Outcome
	 * @throws AssertionError may be thrown if counter does not seem to be right
	 */
	void analyze(String outcomeDesc, Counter<R> counter) throws AssertionError;
}