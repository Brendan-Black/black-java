# Java: typed strings on hot paths

Measured on JDK 25: a wrapper is 16 bytes and about 5 ns when stored, and nothing at all when it doesn't escape the
method, because the JIT removes it. A precompiled `Pattern` against a short string takes nanoseconds but allocates a
`Matcher` (136 bytes) every call, and the JIT did not remove that. A hand-written loop over the same string allocated
nothing. A `URI.create` inside `isLocal()` measured about 50 times the cost of checking the prefix.

A record's `parse` can't avoid running the check twice, once itself and once in the canonical constructor.

For millions of values held in memory, keep a `long[]` or `String[]` inside one class and return the wrapper from the
accessor. Ten million wrappers is 160 MB, and ten million more objects for the GC to trace.
