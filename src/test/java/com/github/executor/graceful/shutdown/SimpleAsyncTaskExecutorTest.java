package com.github.executor.graceful.shutdown;

import java.util.concurrent.CancellationException;

import org.junit.Test;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import com.github.valuelock.test.jcstress.infra.JUnitJCStressTest;

@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "Task executes on active executor")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "Task executes on closed executor")
@State
public class SimpleAsyncTaskExecutorTest extends JUnitJCStressTest {

	private final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
	
	private volatile boolean closed;

	public SimpleAsyncTaskExecutorTest() {
		executor.setTaskTerminationTimeout(1_000_000);
	}
	
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
		} catch (TaskRejectedException | CancellationException e) {
		}
	}

	@Test
	public void test() throws Throwable {
		super.test();
	}
	
}