# Demo of deficiency in graceful shutdown of SimpleAsyncTaskExecutor

[JCStress](https://github.com/openjdk/jcstress)-based `SimpleAsyncTaskExecutorTest` demonstrates a situation when a task, submitted to `SimpleAsyncTaskExecutor`, executes even after the executor is closed. Functionally identical `ThreadPoolExecutorTest` proves that this situation does not happen for `ThreadPoolExecutor`.

Both tests are JUnit-runnable, a small layer, which converts JCStress tests to JUnit, is used for this purpose, but one can run both as barebone JCStress tests.

To run JCStress/JUnit test with Maven, use `mvn test` command. Don't forget that JCStress uses [Annotation Processor](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Processor.html) machinery.
  
In addition, `SimpleAsyncTaskExecutorGracefulShutdown` class demonstrates the same issue by slightly tweaking the original `SimpleAsyncTaskExecutor` code. This tweak mimics a specific threads interleaving which leads to the discussed issue. Functionally identical `ThreadPoolExecutorGracefulShutdown` class shows how the simultaneous submission and closing are handled correctly by `ThreadPoolExecutor`.

## Edit ##

The [issue #36362](https://github.com/spring-projects/spring-framework/issues/36362), opened in Spring Framework issue tracker, has been fixed in [commit 728466d](https://github.com/spring-projects/spring-framework/commit/728466dce0f16672100121abe04a626b158c5d06).
As the corresponding version of jar file might not be easily available, source code of `SimpleAsyncTaskExecutor` is copy-pasted into this project  _without any changes_ . JCStress-based `SimpleAsyncTaskExecutorTest` has been updated correspondingly and it now succeeds. Tweaked version of `SimpleAsyncTaskExecutor` and `SimpleAsyncTaskExecutorGracefulShutdown` standalone test in `com.github.executor.graceful.shutdown` package are no longer relevant.

Note that a running task, submitted to `SimpleAsyncTaskExecutor`, still could see the active status - it stems from the fact that `SimpleAsyncTaskExecutor` does not maintain a distinction between `shutdown` and `terminated` phases, while `ThreadPoolExecutor` does. This is by design of `SimpleAsyncTaskExecutor`.


