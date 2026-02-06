package com.github.valuelock.test.jcstress.infra;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.infra.Status;
import org.openjdk.jcstress.infra.collectors.DiskReadCollector;
import org.openjdk.jcstress.infra.collectors.InProcessCollector;
import org.openjdk.jcstress.infra.collectors.TestResult;
import org.openjdk.jcstress.infra.grading.TestGrading;

/**
 * A JUnit wrapper around JCStress test.
 * It allows to run JCStress test, 
 * i.e. a class annotated with {@link JCStressTest} annotation,
 * as a JUnit test.
 * To do so, a JCStress test could subclass from this class, define a method, 
 * annotated with {@link org.junit.Test Test} and call there {@link #test()} method of this class,
 * for example:
 * <pre> {@code
 * 	@Test
	public void test() throws Throwable {
		super.test();
	}
 * </pre>
 * JCStress parameters could be optionally defined with {@link #addParameter(String, Object)},
 * see the details in the particular implementation of {@link JCStressParameters} used.
 * Additionally, method {@link #runTest()} allows to run a debug version of the test. 
 * <p><b>Implementation details.</b> 
 * After optional parameters are handled by particular {@link JCStressParameters} implementation,
 * {@link org.openjdk.jcstress.Main.main(String[])} method of JCStress framework gets called. 
 * It is supposed to leave the results of execution in the current directory,
 * where it would be retrieved by {@link InProcessCollector}, 
 * and then analyzed by {@link analyzeTestResults} method.
 * As no viable way to establish a dependency between the concrete execution of JCStress test
 * and the results of this test was found, the class does not allow a presence of any "jcstress-results*" files
 * before the execution.
 * Finally, the results in the current directory are removed from the file system.
  */
public abstract class JUnitJCStressTest {

	private final Map<String, OutcomeAnalyzer<String>> outcomeAnalyzers = new HashMap<>();

	private JCStressParameters jcStressParameters = new DefaultJCStressParameters();
	
	private TestRunner testRunner = new DefaultTestRunner();

	public TestRunner getTestRunner() {
		return testRunner;
	}

	public void setTestRunner(TestRunner testRunner) {
		this.testRunner = testRunner;
	}

	public JCStressParameters getJcStressParameters() {
		return jcStressParameters;
	}

	public void setJcStressParameters(JCStressParameters jcStressParameters) {
		this.jcStressParameters = jcStressParameters;
	}

	public Map<String, OutcomeAnalyzer<String>> getOutcomeAnalyzers() {
		return outcomeAnalyzers;
	}

	public void addParameter(String name, Object value) {
		jcStressParameters.addParameter(name, value);
	}

	public void registerOutcomeAnalyzer(String outcomeDesc, OutcomeAnalyzer<String> outcomeAnalyzer) {
		if (null != outcomeAnalyzers.put(outcomeDesc, outcomeAnalyzer)) {
			throw new IllegalArgumentException(OutcomeAnalyzer.class.getSimpleName() + " for @"
					+ Outcome.class.getSimpleName() + " " + outcomeDesc + " already registered");
		}
	}

	// JCStress testing
	
	protected void test() throws Throwable {
		final File currentDir = getCurrentDir();
		final InProcessCollector resultsCollector = new InProcessCollector();

		try {
			runJCStressTest(currentDir, retrieveTestName());
			
			final String[] names = searchJCStressResultFiles(currentDir);
			if (names.length == 0){
				throw new IllegalStateException("No JCStress Test was generated in the directory " + currentDir.getAbsolutePath());
			}
			if (names.length > 1) {
				throw new IllegalStateException("There are more than one JCStress Test results in the directory " + currentDir.getAbsolutePath());
			}
			
			dumpTestResults(resultsCollector, names[0]);
		} finally {
 			cleanUpTestResults(currentDir);
		}

		Map<String, OutcomeAnalyzer<String>> testOutcomeAnalyzers = determineTestOutcomeAnalyzers();
        analyzeTestResults(testOutcomeAnalyzers, resultsCollector.getTestResults());
 	}

	protected void cleanUpTestResults(final File currentDir) {
		for(String fileName : searchJCStressResultFiles(currentDir)){
			new File(fileName).delete();
		}
	}

	protected File getCurrentDir() {
		return new File(".");
	}

	protected String retrieveTestName() {
		Class<?> clazz = getClass();
		String testName = clazz.getName();
		if (clazz.getAnnotation(JCStressTest.class) == null) {
			Class<?>[] nestedClasses = clazz.getDeclaredClasses();
			for(Class<?> nestedClass : nestedClasses) {
				if (nestedClass.getAnnotation(JCStressTest.class) != null) {
					return testName;
				}
			}
			throw new IllegalStateException("Class " + testName + " is not JCStress Test");
		}
		return testName;
	}

	protected void runJCStressTest(File directory, String testName) throws Exception {
		if (searchJCStressResultFiles(directory).length > 0) {
			throw new IllegalStateException("There are already Jcstress results in the directory " + directory.getAbsolutePath());
		}
	
		Main.main(getTestCommandLine(testName));
	}
	
	// parameters
	
	protected String[] getTestCommandLine(String testName) {
		jcStressParameters.setTestName(testName);
		jcStressParameters.retrieveAmbientProperties();
		return jcStressParameters.getCommandLineArguments();
	}
	
	// test results
	
	protected String[] searchJCStressResultFiles(File currentDir) {
		return currentDir.list((d, n)-> n.startsWith("jcstress-results"));
	}

	protected void dumpTestResults(InProcessCollector inProcessCollector, String fileName)
			throws IOException, ClassNotFoundException {
		DiskReadCollector diskReadCollector = null;
		try {
			diskReadCollector = new DiskReadCollector(fileName, inProcessCollector);
			diskReadCollector.dump();
		} finally {
			if (diskReadCollector != null) {
				diskReadCollector.close();
			}
		}
	}

	protected void analyzeTestResults(Map<String, OutcomeAnalyzer<String>> testOutcomeAnalyzers,
			Collection<TestResult> testResults) {
		for (TestResult testResult : testResults) {
			if (testResult.status() != Status.NORMAL) {
				String details = null;
				switch (testResult.status()) {
				case API_MISMATCH:
				case CHECK_TEST_ERROR:
				case TIMEOUT_ERROR:
				case TEST_ERROR:
					details = combineMessages(testResult.getMessages());
					break;
				case VM_ERROR:
					details = combineMessages(testResult.getVmErr());
					break;
				default:
				}
				Assert.fail("Test failed with the status " + testResult.status() 
					+ ((details != null && !details.isEmpty()) ? details : ""));
			}

	       	TestGrading grading = TestGrading.grade(testResult);
        	if(!grading.isPassed) {
        		Assert.fail("Test has not passed" + combineMessages(grading.failureMessages));
        	}
        	
     		// check analyzers
			for (Entry<String, OutcomeAnalyzer<String>> entry : testOutcomeAnalyzers.entrySet()) {
				String id = entry.getKey();
				entry.getValue().analyze(id, testResult.getCounter());
			}
        }
	}

	private String combineMessages(Collection<String> messages) {
		StringJoiner details = new StringJoiner("; ", ": ", "");
		for(String message : messages) {
			details.add(message);
		}
		return details.toString();
	}
	
	// Outcome Analyzers

	protected Map<String, OutcomeAnalyzer<String>> determineTestOutcomeAnalyzers() {
		final Map<String, OutcomeAnalyzer<String>> testOutcomeAnalyzers = new HashMap<>();

		for (Annotation anno : getClass().getAnnotations()) {
			if (anno.annotationType() == Outcome.Outcomes.class) {
				for (Outcome outcome : ((Outcome.Outcomes) anno).value()) {
					final OutcomeAnalyzer<String> outcomeAnalyzer = outcomeAnalyzers.get(outcome.desc());
					if (outcomeAnalyzer != null) {
						for (String id : outcome.id()) {
							testOutcomeAnalyzers.put(id, outcomeAnalyzer);
						}
					}
				}
			}
		}
		
		return testOutcomeAnalyzers;
	}
	
	// debug execution of the test
	
	public Object runTest() throws InterruptedException, TimeoutException {
		return testRunner.runTest(this);
	}

}
