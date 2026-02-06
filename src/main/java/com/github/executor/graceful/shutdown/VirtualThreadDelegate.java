package com.github.executor.graceful.shutdown;

import java.util.concurrent.ThreadFactory;

public final class VirtualThreadDelegate {

	public VirtualThreadDelegate() {
	}

	public ThreadFactory virtualThreadFactory() {
		return Thread.ofVirtual().factory();
	}

	public ThreadFactory virtualThreadFactory(String threadNamePrefix) {
		return Thread.ofVirtual().name(threadNamePrefix, 0).factory();
	}

	public Thread newVirtualThread(String name, Runnable task) {
		return Thread.ofVirtual().name(name).unstarted(task);
	}

}
