# Java: elapsed time

`System.nanoTime()` is the monotonic source. Put it behind the clock interface rather than swapping it for
`InstantSource.system()`, which is a wall clock, as are `Instant.now()` and `System.currentTimeMillis()`.
