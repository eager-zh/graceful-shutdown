# Demo of deficiency in graceful shutdown of SimpleAsyncTaskExecutor

[JCStress](https://github.com/openjdk/jcstress)-based `SimpleAsyncTaskExecutorTest` demonstrates a situation when a task, submitted to `SimpleAsyncTaskExecutor`, executes even after the executor is closed. Functionally identical `ThreadPoolExecutorTest` proves that this situation does not happen for `ThreadPoolExecutor`.

Both tests are JUnit-runnable, a small layer, which converts JCStress tests to JUnit, is used for this purpose, but one can run both as barebone JCStress tests.

To run JCStress/JUnit test with Maven, use `mvn test` command. Don't forget that JCStress uses [Annotation Processor](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Processor.html) machinery.
  
In addition, `SimpleAsyncTaskExecutorGracefulShutdown` class demonstrates the same issue by slightly tweaking the original `SimpleAsyncTaskExecutor` code. This tweak mimics a specific threads interleaving which leads to the discussed issue. Functionally identical `ThreadPoolExecutorGracefulShutdown` class shows how the simultaneous submission and closing are handled correctly by `ThreadPoolExecutor`.

