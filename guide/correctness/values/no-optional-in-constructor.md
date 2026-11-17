# No `Optional` in a constructor

Java only. `Optional<Anything>` never appears in a constructor's parameter list, record components included, since they
are its canonical constructor. No exception, and no deference to the code around you.

`Optional` is a return-type tool. As a parameter it is one more thing that can itself be null, so the "optional" value
has three states (present, empty, null) and the type admits to two. At scale: `new Address(Optional.empty(),
Optional.empty(), Optional.of(city), Optional.empty())` at every construction site, every fixture and mapper wrapping
and unwrapping, the ceremony burying the data, and still nothing stopping a bare `null` passed for one of them.

Take the plain value, `@Nullable`, and return `Optional` from an accessor, as `MyRecord` does in
[nulls](../../conventions/values/nulls.md).

The JDK has no `@Nullable`. If your project has none and doesn't want the dependency, split the constructor (one with
the value, a named factory without it), or use a named default with an `Optional` accessor (`Duration.ZERO` for an
unknown length, read through `knownLength()`). A default is a weaker promise than an annotation, so keep the accessor
next to it.

The build holds this line, not review: [this repo's checks](../../../README.md#checks) make an `Optional` constructor
parameter a compiler error.
