package com.github.executor.graceful.shutdown;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.mutable.MutableObject;

/**
 * Demonstrates correct graceful shutdown implemented by 
 * {@link java.util.concurrent.ThreadPoolExecutor}.
 * Compare with {@link SimpleAsyncTaskExecutorGracefulShutdown}.
 */
public class ThreadPoolExecutorGracefulShutdown {

	private static final int DELAY_BETWEEN_SUBMISSION_AND_CLOSING = 3;

	public static void main(String[] args) throws Throwable {
		final ExecutorService executor = Executors.newCachedThreadPool();
		final MutableObject<Future<?>> future = new MutableObject<>();
		final Thread t1 = Thread.ofPlatform().start(() -> {
			future.setValue(executor.submit(() -> {
				if (!executor.isTerminated()) {
					System.out.println("OK. Executor is active");
				} else {
					System.err.println("Executor is CLOSED while the task is in progress!");
				}
			}));
		});
		
		Thread.sleep(Duration.ofSeconds(DELAY_BETWEEN_SUBMISSION_AND_CLOSING));
		
		final Thread t2 = Thread.ofPlatform().start(() -> {
			System.out.println("Executor closing...");
			executor.close();
			if (!executor.isTerminated()) {
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
