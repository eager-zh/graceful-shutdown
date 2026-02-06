package com.github.executor.graceful.shutdown;

import java.io.Serializable;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrencyThrottleSupport;
import org.springframework.util.CustomizableThreadCreator;

/**
 * Tweaked version of original {@link org.springframework.core.task.SimpleAsyncTaskExecutor}
 * which helps to demonstrate an issue with a task which appears to be active 
 * while the executor is closed.
 * 
 * </p></p> 
 * A delay is inserted into {@link TaskTrackingRunnable#run()} method 
 * before adding current thread to {@link #activeThreads} collection. 
 * to emulate specific threads interleaving. 
 * This delay should be greater than a delay 
 * between start of a closing thread and start of submission thread 
 * 
 * @see SimpleAsyncTaskExecutorGracefulShutdown
 * @see org.springframework.core.task.SimpleAsyncTaskExecutor  
 */
@SuppressWarnings("serial")
public class SimpleAsyncTaskExecutor extends CustomizableThreadCreator
		implements AsyncTaskExecutor, Serializable, AutoCloseable {

	/**
	 * Permit any number of concurrent invocations: that is, don't throttle concurrency.
	 * @see ConcurrencyThrottleSupport#UNBOUNDED_CONCURRENCY
	 */
	public static final int UNBOUNDED_CONCURRENCY = ConcurrencyThrottleSupport.UNBOUNDED_CONCURRENCY;

	/**
	 * Switch concurrency 'off': that is, don't allow any concurrent invocations.
	 * @see ConcurrencyThrottleSupport#NO_CONCURRENCY
	 */
	public static final int NO_CONCURRENCY = ConcurrencyThrottleSupport.NO_CONCURRENCY;

	public static final int THREAD_REGISTRATION_DELAY = 5;

	/** Internal concurrency throttle used by this executor. */
	private final ConcurrencyThrottleAdapter concurrencyThrottle = new ConcurrencyThrottleAdapter();

	private @Nullable VirtualThreadDelegate virtualThreadDelegate;

	private @Nullable ThreadFactory threadFactory;

	private @Nullable TaskDecorator taskDecorator;

	private long taskTerminationTimeout;

	private @Nullable Set<Thread> activeThreads;

	private boolean cancelRemainingTasksOnClose = false;

	private boolean rejectTasksWhenLimitReached = false;

	private volatile boolean active = true;


	/**
	 * Create a new SimpleAsyncTaskExecutor with default thread name prefix.
	 */
	public SimpleAsyncTaskExecutor() {
		super();
	}

	/**
	 * Create a new SimpleAsyncTaskExecutor with the given thread name prefix.
	 * @param threadNamePrefix the prefix to use for the names of newly created threads
	 */
	public SimpleAsyncTaskExecutor(String threadNamePrefix) {
		super(threadNamePrefix);
	}

	/**
	 * Create a new SimpleAsyncTaskExecutor with the given external thread factory.
	 * @param threadFactory the factory to use for creating new Threads
	 */
	public SimpleAsyncTaskExecutor(ThreadFactory threadFactory) {
		this.threadFactory = threadFactory;
	}


	/**
	 * Switch this executor to virtual threads. Requires Java 21 or higher.
	 * <p>The default is {@code false}, indicating platform threads.
	 * Set this flag to {@code true} in order to create virtual threads instead.
	 * @since 6.1
	 */
	public void setVirtualThreads(boolean virtual) {
		this.virtualThreadDelegate = (virtual ? new VirtualThreadDelegate() : null);
	}

	/**
	 * Specify an external factory to use for creating new Threads,
	 * instead of relying on the local properties of this executor.
	 * <p>You may specify an inner ThreadFactory bean or also a ThreadFactory reference
	 * obtained from JNDI (on a Jakarta EE server) or some other lookup mechanism.
	 * @see #setThreadNamePrefix
	 * @see #setThreadPriority
	 */
	public void setThreadFactory(@Nullable ThreadFactory threadFactory) {
		this.threadFactory = threadFactory;
	}

	/**
	 * Return the external factory to use for creating new Threads, if any.
	 */
	public final @Nullable ThreadFactory getThreadFactory() {
		return this.threadFactory;
	}

	/**
	 * Specify a custom {@link TaskDecorator} to be applied to any {@link Runnable}
	 * about to be executed.
	 * <p>Note that such a decorator is not necessarily being applied to the
	 * user-supplied {@code Runnable}/{@code Callable} but rather to the actual
	 * execution callback (which may be a wrapper around the user-supplied task).
	 * <p>The primary use case is to set some execution context around the task's
	 * invocation, or to provide some monitoring/statistics for task execution.
	 * <p><b>NOTE:</b> Exception handling in {@code TaskDecorator} implementations
	 * is limited to plain {@code Runnable} execution via {@code execute} calls.
	 * In case of {@code #submit} calls, the exposed {@code Runnable} will be a
	 * {@code FutureTask} which does not propagate any exceptions; you might
	 * have to cast it and call {@code Future#get} to evaluate exceptions.
	 * @since 4.3
	 */
	public void setTaskDecorator(TaskDecorator taskDecorator) {
		this.taskDecorator = taskDecorator;
	}

	/**
	 * Specify a timeout (in milliseconds) for task termination when closing
	 * this executor. The default is 0, not waiting for task termination at all.
	 * <p>Note that a concrete >0 timeout specified here will lead to the
	 * wrapping of every submitted task into a task-tracking runnable which
	 * involves considerable overhead in case of a high number of tasks.
	 * However, for a modest level of submissions with longer-running
	 * tasks, this is feasible in order to arrive at a graceful shutdown.
	 * <p>Note that {@code SimpleAsyncTaskExecutor} does not participate in
	 * a coordinated lifecycle stop but rather just awaits task termination
	 * on {@link #close()}.
	 * @param timeout the timeout in milliseconds
	 * @since 6.1
	 * @see #close()
	 * @see #setCancelRemainingTasksOnClose
	 * @see org.springframework.scheduling.concurrent.ExecutorConfigurationSupport#setAwaitTerminationMillis
	 */
	public void setTaskTerminationTimeout(long timeout) {
		Assert.isTrue(timeout >= 0, "Timeout value must be >=0");
		this.taskTerminationTimeout = timeout;
		trackActiveThreadsIfNecessary();
	}

	/**
	 * Specify whether to cancel remaining tasks on close: that is, whether to
	 * interrupt any active threads at the time of the {@link #close()} call.
	 * <p>The default is {@code false}, not tracking active threads at all or
	 * just interrupting any remaining threads that still have not finished after
	 * the specified {@link #setTaskTerminationTimeout taskTerminationTimeout}.
	 * Switch this to {@code true} for immediate interruption on close, either in
	 * combination with a subsequent termination timeout or without any waiting
	 * at all, depending on whether a {@code taskTerminationTimeout} has been
	 * specified as well.
	 * @since 6.2.11
	 * @see #close()
	 * @see #setTaskTerminationTimeout
	 * @see org.springframework.scheduling.concurrent.ExecutorConfigurationSupport#setWaitForTasksToCompleteOnShutdown
	 */
	public void setCancelRemainingTasksOnClose(boolean cancelRemainingTasksOnClose) {
		this.cancelRemainingTasksOnClose = cancelRemainingTasksOnClose;
		trackActiveThreadsIfNecessary();
	}

	/**
	 * Specify whether to reject tasks when the concurrency limit has been reached,
	 * throwing {@link TaskRejectedException} (which extends the common
	 * {@link java.util.concurrent.RejectedExecutionException})
	 * on any further execution attempts.
	 * <p>The default is {@code false}, blocking the caller until the submission can
	 * be accepted. Switch this to {@code true} for immediate rejection instead.
	 * @since 6.2.6
	 * @see #setConcurrencyLimit
	 */
	public void setRejectTasksWhenLimitReached(boolean rejectTasksWhenLimitReached) {
		this.rejectTasksWhenLimitReached = rejectTasksWhenLimitReached;
	}

	/**
	 * Set the maximum number of parallel task executions allowed.
	 * The default of -1 indicates no concurrency limit at all.
	 * <p>This is the equivalent of a maximum pool size in a thread pool,
	 * preventing temporary overload of the thread management system.
	 * However, in contrast to a thread pool with a managed task queue,
	 * this executor will block the submitter until the task can be
	 * accepted when the configured concurrency limit has been reached.
	 * If you prefer queue-based task hand-offs without such blocking,
	 * consider using a {@code ThreadPoolTaskExecutor} instead.
	 * @see #UNBOUNDED_CONCURRENCY
	 * @see org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor#setMaxPoolSize
	 */
	public void setConcurrencyLimit(int concurrencyLimit) {
		this.concurrencyThrottle.setConcurrencyLimit(concurrencyLimit);
	}

	/**
	 * Return the maximum number of parallel task executions allowed.
	 */
	public final int getConcurrencyLimit() {
		return this.concurrencyThrottle.getConcurrencyLimit();
	}

	/**
	 * Return whether the concurrency throttle is currently active.
	 * @return {@code true} if the concurrency limit for this instance is active
	 * @see #getConcurrencyLimit()
	 * @see #setConcurrencyLimit
	 */
	public final boolean isThrottleActive() {
		return this.concurrencyThrottle.isThrottleActive();
	}

	/**
	 * Return whether this executor is still active, i.e. not closed yet,
	 * and therefore accepts further task submissions. Otherwise, it is
	 * either in the task termination phase or entirely shut down already.
	 * @since 6.1
	 * @see #setTaskTerminationTimeout
	 * @see #close()
	 */
	public boolean isActive() {
		return this.active;
	}

	/**
	 * Track active threads only when a task termination timeout has been
	 * specified or interruption of remaining threads has been requested.
	 */
	private void trackActiveThreadsIfNecessary() {
		this.activeThreads = (this.taskTerminationTimeout > 0 || this.cancelRemainingTasksOnClose ?
				ConcurrentHashMap.newKeySet() : null);
	}


	/**
	 * Executes the given task, within a concurrency throttle
	 * if configured (through the superclass's settings).
	 * @see #doExecute(Runnable)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void execute(Runnable task) {
		execute(task, TIMEOUT_INDEFINITE);
	}

	/**
	 * Executes the given task, within a concurrency throttle
	 * if configured (through the superclass's settings).
	 * <p>Executes urgent tasks (with 'immediate' timeout) directly,
	 * bypassing the concurrency throttle (if active). All other
	 * tasks are subject to throttling.
	 * @see #TIMEOUT_IMMEDIATE
	 * @see #doExecute(Runnable)
	 */
	@Deprecated(since = "5.3.16")
	@Override
	public void execute(Runnable task, long startTimeout) {
		Assert.notNull(task, "Runnable must not be null");
		if (!isActive()) {
			throw new TaskRejectedException(getClass().getSimpleName() + " has been closed already");
		}

		Runnable taskToUse = (this.taskDecorator != null ? this.taskDecorator.decorate(task) : task);
		if (isThrottleActive() && startTimeout > TIMEOUT_IMMEDIATE) {
			this.concurrencyThrottle.beforeAccess();
			try {
				doExecute(new TaskTrackingRunnable(taskToUse));
			}
			catch (Throwable ex) {
				// Release concurrency permit if thread creation fails
				this.concurrencyThrottle.afterAccess();
				throw new TaskRejectedException(
						"Failed to start execution thread for task: " + task, ex);
			}
		}
		else if (this.activeThreads != null) {
			doExecute(new TaskTrackingRunnable(taskToUse));
		}
		else {
			doExecute(taskToUse);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public Future<?> submit(Runnable task) {
		FutureTask<Object> future = new FutureTask<>(task, null);
		execute(future, TIMEOUT_INDEFINITE);
		return future;
	}

	@SuppressWarnings("deprecation")
	@Override
	public <T> Future<T> submit(Callable<T> task) {
		FutureTask<T> future = new FutureTask<>(task);
		execute(future, TIMEOUT_INDEFINITE);
		return future;
	}

	/**
	 * Template method for the actual execution of a task.
	 * <p>The default implementation creates a new Thread and starts it.
	 * @param task the Runnable to execute
	 * @see #newThread
	 * @see Thread#start()
	 */
	protected void doExecute(Runnable task) {
		newThread(task).start();
	}

	/**
	 * Create a new Thread for the given task.
	 * @param task the Runnable to create a Thread for
	 * @return the new Thread instance
	 * @since 6.1
	 * @see #setVirtualThreads
	 * @see #setThreadFactory
	 * @see #createThread
	 */
	protected Thread newThread(Runnable task) {
		if (this.virtualThreadDelegate != null) {
			return this.virtualThreadDelegate.newVirtualThread(nextThreadName(), task);
		}
		else {
			return (this.threadFactory != null ? this.threadFactory.newThread(task) : createThread(task));
		}
	}

	/**
	 * This close method tracks the termination of active threads if a concrete
	 * {@link #setTaskTerminationTimeout task termination timeout} has been set.
	 * Otherwise, it is not necessary to close this executor.
	 * @since 6.1
	 */
	@Override
	public void close() {
		if (this.active) {
			this.active = false;
			Set<Thread> threads = this.activeThreads;
			if (threads != null) {
				if (this.cancelRemainingTasksOnClose) {
					// Early interrupt for remaining tasks on close
					threads.forEach(Thread::interrupt);
				}
				if (this.taskTerminationTimeout > 0) {
					synchronized (threads) {
						try {
							if (!threads.isEmpty()) {
								threads.wait(this.taskTerminationTimeout);
							}
						}
						catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
						}
					}
					if (!this.cancelRemainingTasksOnClose) {
						// Late interrupt for remaining tasks after timeout
						threads.forEach(Thread::interrupt);
					}
				}
			}
		}
	}


	/**
	 * Subclass of the general ConcurrencyThrottleSupport class,
	 * making {@code beforeAccess()} and {@code afterAccess()}
	 * visible to the surrounding class.
	 */
	private class ConcurrencyThrottleAdapter extends ConcurrencyThrottleSupport {

		@Override
		protected void beforeAccess() {
			super.beforeAccess();
		}

		@Override
		protected void onLimitReached() {
			if (rejectTasksWhenLimitReached) {
				throw new TaskRejectedException("Concurrency limit reached: " + getConcurrencyLimit());
			}
			super.onLimitReached();
		}

		@Override
		protected void afterAccess() {
			super.afterAccess();
		}
	}


	/**
	 * Decorates a target task with active thread tracking
	 * and concurrency throttle management, if necessary.
	 */
	private class TaskTrackingRunnable implements Runnable {

		private final Runnable task;

		public TaskTrackingRunnable(Runnable task) {
			Assert.notNull(task, "Task must not be null");
			this.task = task;
		}

		@Override
		public void run() {
			Set<Thread> threads = activeThreads;
			Thread thread = null;
			if (threads != null) {
				thread = Thread.currentThread();
				
				// FIXME
				try {
					Thread.sleep(Duration.ofSeconds(THREAD_REGISTRATION_DELAY*2));
				} catch (InterruptedException e) {
				}
				
				threads.add(thread);
			}
			try {
				this.task.run();
			}
			finally {
				if (threads != null) {
					threads.remove(thread);
					if (!isActive()) {
						synchronized (threads) {
							if (threads.isEmpty()) {
								threads.notify();
							}
						}
					}
				}
				concurrencyThrottle.afterAccess();
			}
		}
	}

}
