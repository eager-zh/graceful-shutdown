package com.github.valuelock.test.jcstress.infra;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.Signal;

/**
 * A simple implementation of {@link TestRunner}, which retrieves the
 * {@link Actor} and {@link Signal} methods from the test's class creates and
 * starts the correspondent threads, wait for their completion for certain
 * timeout, analyzes the result, which is supposed to of be one of the types,
 * annotated with {@link org.openjdk.jcstress.annotations.Result}, against the
 * test's class {@link Outcome} annotations, prints the result, and returns it
 * to the caller. If the test defines {@link Arbiter} method, it would be called
 * after all {@link Actor}/{@link Signal} threads complete.
 */
public class DefaultTestRunner implements TestRunner {

	private long timeout;

	private Object test;

	private Class<?> jcStressClass;

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	@Override
	public Object runTest(Object test) throws InterruptedException, TimeoutException {
		this.test = test;
		jcStressClass = test.getClass();
		JCStressTest jcstressTest = jcStressClass.getAnnotation(JCStressTest.class);
		if (jcstressTest == null) {
			throw new IllegalArgumentException("Class " + jcStressClass.getName() + " is not JCStressTest");
		}
		switch (jcstressTest.value()) {
		case Continuous:
			return runContiniousTest();
		case Termination:
			return runTerminationTest();
		default:
			throw new IllegalStateException("Unexpected JCStressTest mode " + jcstressTest.value());
		}
	}

	protected String runTerminationTest() throws InterruptedException {
		List<Method> actors = getAnnotatedMethods(jcStressClass, Actor.class);
		if (actors.size() != 1) {
			throw new IllegalStateException("There are no or more than one method annotated with @"
					+ Actor.class.getSimpleName() + " in class " + jcStressClass.getName());
		}
		Thread actor = new Thread(() -> {
			invoke(actors.get(0), null);
		});

		List<Method> signallers = getAnnotatedMethods(jcStressClass, Signal.class);
		if (signallers.size() != 1) {
			throw new IllegalStateException("There are no or more than one method annotated with @"
					+ Signal.class.getSimpleName() + " in class " + jcStressClass);
		}
		Thread signaller = new Thread(() -> {
			invoke(signallers.get(0), null);
		});

		actor.start();
		signaller.start();
		signaller.join(timeout);
		actor.join(timeout);
		String result = signaller.isAlive() || actor.isAlive() ? "STALE" : "TERMINATED";
		printResult(result);
		return result;
	}

	protected Object runContiniousTest() throws InterruptedException, TimeoutException {
		final List<Thread> threads = new ArrayList<>();
		Object result = null;
		for (final Method method : getAnnotatedMethods(jcStressClass, Actor.class)) {
			final Object thisResult = instantiateResult(result, method);
			if (result == null) {
				result = thisResult;
			}
			threads.add(new Thread(() -> {
				invoke(method, thisResult);
			}));
			System.out.println("Result for method " + method + ":" + result);
		}

		for (Thread t : threads) {
			t.setUncaughtExceptionHandler((t1, e) -> {
				e.printStackTrace();
			});
			t.start();
		}

		for (Thread t : threads) {
			t.join(timeout);
			if (t.isAlive())
				throw new TimeoutException("Thread " + t.getName() + " hangs");
		}

		for (final Method method : getAnnotatedMethods(jcStressClass, Arbiter.class)) {
			result = instantiateResult(result, method);
			System.out.println("Result for method " + method + ":" + result);
			invoke(method, result);
		}

		printResult(result);

		return result;
	}

	protected void printResult(Object result) {
		String outcomeId = result.toString();
		for (Outcome nextOutcome : jcStressClass.getAnnotationsByType(Outcome.class)) {
			for (String id : nextOutcome.id()) {
				if (id.toString().equals(outcomeId)) {
					System.out.println(nextOutcome.expect().toString() + " : " + nextOutcome.desc());
					return;
				}
			}
		}
		System.out.println("Outcome " + outcomeId + " is not described in the test");
	}

	protected void invoke(Method method, Object result) {
		try {
			if (result == null)
				method.invoke(test);
			else
				method.invoke(test, result);
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getTargetException());
		}
	}

	protected Object instantiateResult(Object result, Method method) {
		Parameter[] parameters = method.getParameters();
		if (parameters.length > 1) {
			throw new IllegalStateException("Method " + method + " annotated with @" + Actor.class.getSimpleName()
					+ " or @" + Arbiter.class.getSimpleName() + " has more than one paramater");
		}
		if (parameters.length == 1) {
			Class<?> resultType = parameters[0].getType();
			if (result != null && result.getClass() != resultType) {
				throw new IllegalStateException("Method " + method
						+ " parameter type mismatches the parameter type of other methods annotated with @"
						+ Actor.class.getSimpleName() + " or @" + Arbiter.class.getSimpleName());
			}
			if (result == null) {
				result = instantiateResult(resultType);
			}
			return result;
		}
		return null;
	}

	private static Object instantiateResult(Class<?> resultType) {
		try {
			return resultType.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("Cannot instatiate Result for type " + resultType.getSimpleName(), e);
		}
	}

	private static List<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotation) {
		List<Method> methods = new ArrayList<>();
		for (final Method method : clazz.getMethods()) {
			if (method.isAnnotationPresent(annotation)) {
				methods.add(method);
			}
		}
		return methods;
	}

}
