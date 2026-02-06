package com.github.executor.graceful.shutdown;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.junit.Test;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

import com.github.valuelock.test.jcstress.infra.JUnitJCStressTest;

@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "Task executes on active executor")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "Task executes on closed executor")
@State
public class ThreadPoolExecutorTest extends JUnitJCStressTest {

	private final ExecutorService executor = Executors.newCachedThreadPool();
	
	private volatile boolean closed;

	@Actor
	public void actor1(I_Result r) {
		executor.close();
		closed = true;
	}

	@Actor
	public void actor2(I_Result r) {
		try {
			executor.submit(() -> {
				if (closed)
					r.r1 = 1;
			});
		} catch (RejectedExecutionException e) {
		}
	}

	@Test
	public void test() throws Throwable {
		super.test();
	}

}