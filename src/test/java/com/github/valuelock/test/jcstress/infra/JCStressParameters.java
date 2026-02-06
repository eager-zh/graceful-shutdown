package com.github.valuelock.test.jcstress.infra;

/**
 * JCStress parameters handler. Allows to set parameters for JCStress execution
 * in the form name-value pairs along with the name of the test. Returns
 * processed parameters in a form of String array. Optionally retrieves the
 * parameters from environment or system variables and the kind.
 */
public interface JCStressParameters {

	/**
	 * @return String array of parameters in a form ready to pass to main method of
	 *         JCStress framework
	 */
	String[] getCommandLineArguments();

	/**
	 * Retrieves the JCStress parameters from environment or system variables and
	 * the kind.
	 */
	void retrieveAmbientProperties();

	/**
	 * Sets JCStress parameter in a form of name-value pair
	 * 
	 * @param name  name of parameter
	 * @param value value of parameter
	 */
	void addParameter(String name, Object value);

	/**
	 * @param testName name of the test in a form accepted by JCStress framework
	 */
	void setTestName(String testName);

}
