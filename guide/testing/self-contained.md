# Java: self-contained tests

- Elapsed time: put `System.nanoTime()` behind the clock interface rather than swapping it for
  `InstantSource.system()`, which is a wall clock.
- No `Thread.sleep` in tests: wait on a `CountDownLatch`, a readiness check, or a polled condition. ArchUnit can ban it.
- Containers: Testcontainers.
