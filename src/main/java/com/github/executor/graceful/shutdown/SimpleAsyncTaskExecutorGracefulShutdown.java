package com.github.executor.graceful.shutdown;

import java.time.Duration;
import java.util.concurrent.Future;

import org.apache.commons.lang3.mutable.MutableObject;

/**
 * Demonstrates an issue with task being active after 
 * {@link SimpleAsyncTaskExecutor} was closed.
 * The original {@link org.springframework.core.task.SimpleAsyncTaskExecutor} 
 * was tweaked to emulate threads interleaving leading to this issue.
 * 
 * <p/><p/> The delay between the start of a closing thread should be 
 * and a start of submission thread should be smaller than a delay 
 * inserted into {@link SimpleAsyncTaskExecutor}.  
 * 
 * @see SimpleAsyncTaskExecutor
 * @see org.springframework.core.task.SimpleAsyncTaskExecutor
 */
public class SimpleAsyncTaskExecutorGracefulShutdown {

	public static void main(String[] args) throws Throwable {
		final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
		executor.setTaskTerminationTimeout(1_000_000);

		final MutableObject<Future<?>> future = new MutableObject<>();
		final Thread t1 = Thread.ofPlatform().start(() -> {
			future.setValue(executor.submit(() -> {
				if (executor.isActive()) {
					System.out.println("OK. Executor is active");
				} else {
					System.err.println("Executor is CLOSED while the task is in progress!");
				}
			}));
		});
		
		Thread.sleep(Duration.ofSeconds(SimpleAsyncTaskExecutor.THREAD_REGISTRATION_DELAY));
		
		final Thread t2 = Thread.ofPlatform().start(() -> {
			System.out.println("Executor closing...");
			executor.close();
			if (executor.isActive()) {
				System.err.println("Executor is active after closing!");
			} else {
				System.out.println("OK. Executor is closed");
			}
		});
		
		t1.join();
		t2.join();
		future.get().get();
		
		System.out.println("Completed");
	}

}
