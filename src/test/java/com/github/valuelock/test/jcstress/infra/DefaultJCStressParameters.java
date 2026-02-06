package com.github.valuelock.test.jcstress.infra;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An implementation of {@link JCStressParameters}.
 * {@link #addParameter(String, Object)} method expects the names, compatible
 * with JCStress command line parameters and type of the values compatible with
 * JCStress parameters types.
 * <p/>
 * Additional parameters are taken from System properties. In this case,
 * parameter name should be prefixed with "jcstress." and the String value of
 * the parameter will be converted to the type of JCStress parameter.
 */
public class DefaultJCStressParameters implements JCStressParameters {

	private static final String OPTION_PREFIX = "-";

	private static class JCStressParameter<T> {

		private final String name;

		private final Class<T> type;

		private T value;

		public JCStressParameter(String name, Class<T> type) {
			this.name = name;
			this.type = type;
		}

		public String getName() {
			return name;
		}

		public boolean isSet() {
			return value != null;
		}

		@Override
		public String toString() {
			return name + " " + (isSet() ? value.toString() : "<Not set>");
		}

		@SuppressWarnings("unchecked")
		public void setValue(Object value) {
			if (type.isInstance(value)) {
				this.value = (T) value;
			} else {
				throw new IllegalArgumentException(
						"Value " + value + " of parameter " + name + " is not of type" + type.getSimpleName());
			}
		}

		public void setValue(String value) {
			try {
				setValue(type.getMethod("valueOf", String.class).invoke(null, value));
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				try {
					setValue(type.getConstructor(String.class).newInstance(value));
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException | SecurityException e1) {
					throw new IllegalArgumentException("Cannot convert a value " + value + "of System Property " + name
							+ " to type " + type.getClass().getSimpleName());
				}
			}
		}

		public List<String> getCommandLineArguments() {
			List<String> args = new ArrayList<>();
			if (isSet()) {
				args.add("-" + name);
				args.add(value.toString());
			}
			return args;
		}

	}

	private static class StringParameter extends JCStressParameter<String> {

		public StringParameter(String name) {
			super(name, String.class);
		}

		public void setValue(String value) {
			setValue((Object) value);
		}
	}

	private static class StringArrayParameter extends JCStressParameter<String[]> {

		private final List<String> parameters = new ArrayList<>();

		public StringArrayParameter(String name) {
			super(name, String[].class);
		}

		@Override
		public void setValue(String value) {
			for (String token : value.split(",")) {
				parameters.add(token.trim());
			}
		}

		@Override
		public void setValue(Object value) {
			if (value instanceof String) {
				setValue((String) value);
			} else if (value instanceof String[]) {
				parameters.addAll(Arrays.asList((String) value));
			} else if (value instanceof Collection) {
				for (Object objValue : (Collection<?>) value) {
					if (objValue instanceof String) {
						setValue((String) objValue);
					} else {
						throw new IllegalArgumentException(
								"Value " + objValue + " of parameter " + getName() + " is not of type String");
					}
				}
			}
		}

		@Override
		public List<String> getCommandLineArguments() {
			List<String> args = new ArrayList<>();
			for (String parameter : parameters) {
				args.add(OPTION_PREFIX + getName());
				args.add(parameter);
			}
			return args;
		}

	}

	private static class ValuelessParameter extends JCStressParameter<Void> {

		private boolean valueSet;

		public ValuelessParameter(String name) {
			super(name, Void.class);
		}

		@Override
		public void setValue(String value) {
			valueSet = Boolean.parseBoolean(value);
		}

		@Override
		public void setValue(Object value) {
			setValue(value == null ? null : value.toString());
		}

		@Override
		public List<String> getCommandLineArguments() {
			List<String> args = new ArrayList<>();
			if (valueSet) {
				args.add(OPTION_PREFIX + getName());
			}
			return args;
		}
	}

	private final Map<String, JCStressParameter<?>> parameters = new HashMap<>();

	public DefaultJCStressParameters() {
		registerParameters();
	}

	protected void registerParameters() {
		registerParameter(new ValuelessParameter("v"));
		registerParameter(new ValuelessParameter("vv"));
		registerParameter(new ValuelessParameter("vvv"));
		registerParameter(new ValuelessParameter("h"));
		registerParameter(new JCStressParameter<>("strideSize", Integer.class));
		registerParameter(new JCStressParameter<>("strideCount", Integer.class));
		registerParameter(new StringParameter("spinStyle"));
		registerParameter(new StringParameter("r"));
		registerParameter(new StringParameter("m"));
		registerParameter(new StringArrayParameter("jvmArgs"));
		registerParameter(new StringArrayParameter("jvmArgsPrepend"));
		registerParameter(new StringParameter("af"));
		registerParameter(new JCStressParameter<>("time", Integer.class));
		registerParameter(new JCStressParameter<>("iters", Integer.class));
		registerParameter(new JCStressParameter<>("c", Integer.class));
		registerParameter(new JCStressParameter<>("hs", Integer.class));
		registerParameter(new JCStressParameter<>("f", Integer.class));
		registerParameter(new JCStressParameter<>("fsm", Integer.class));
		registerParameter(new JCStressParameter<>("f", Integer.class));
		registerParameter(new JCStressParameter<>("sc", Boolean.class));

		registerParameter(new StringParameter("t"));
	}

	@Override
	public void setTestName(String testName) {
		addParameter("t", testName);
	}

	private void registerParameter(JCStressParameter<?> parameter) {
		parameters.put(parameter.getName(), parameter);
	}

	@Override
	public void addParameter(String name, Object value) {
		JCStressParameter<?> parameter = parameters.get(name);
		if (parameter == null) {
			throw new IllegalArgumentException("Unknown JCStress parameter " + name);
		}
		parameter.setValue(value);
	}

	@Override
	public void retrieveAmbientProperties() {
		for (Entry<String, JCStressParameter<?>> entry : parameters.entrySet()) {
			String name = entry.getKey();
			String value = System.getProperty("jcstress." + name);
			if (value != null) {
				JCStressParameter<?> parameter = entry.getValue();
				if (!parameter.isSet()) {
					parameter.setValue(value);
				}
			}
		}
	}

	@Override
	public String[] getCommandLineArguments() {
		List<String> args = new ArrayList<>();
		for (JCStressParameter<?> parameter : parameters.values()) {
			args.addAll(parameter.getCommandLineArguments());
		}
		return args.toArray(new String[] {});
	}

}