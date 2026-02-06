package com.github.valuelock.test.jcstress.infra;

import org.junit.Assert;
import org.openjdk.jcstress.util.Counter;

/**
 * An experimental feature atop of JCStress. This {@link OutcomeAnalyzer} expects that certain
 * {@link org.openjdk.jcstress.annotations.Outcome @Outcome} <i>must appear</i>
 * in the results of JCStress test and throws a test error if such Outcome does
 * not.
 * <p/>
 * As JCStress, in fact, implies some ideology rather than explicitly declares
 * it, it is difficult to determine for the author whether above expectation is
 * valid according to such ideology.
 * <p/>
 * To enable MustHappenOutcomeAnalyzer
 * {@link org.openjdk.jcstress.annotations.Outcome#desc() desc} attribute of an
 * Outcome should be defined as {@link #MUST_HAPPEN_OUTCOME} string.
 */
public class MustHappenOutcomeAnalyzer implements OutcomeAnalyzer<String> {

	public static final String MUST_HAPPEN_OUTCOME = "This must happen!";

	@Override
	public void analyze(String mustHappenOutcomeDesc, Counter<String> counter) {
		if (counter.count(mustHappenOutcomeDesc) == 0) {
			Assert.fail("Outcome ID \"" + mustHappenOutcomeDesc + "\" must happen");
		}
	}

}