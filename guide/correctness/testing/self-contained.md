# Java: self-contained tests

- No `Thread.sleep` in tests: wait on a `CountDownLatch`, a readiness check, or a polled condition. ArchUnit can ban it.
- Containers: Testcontainers.
