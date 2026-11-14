# Java: no `Optional` in a constructor

`Optional<Anything>` never appears in a constructor's parameter list, record components included, since they are its
canonical constructor. Take the plain value, `@Nullable`, and return `Optional` from an accessor, as `MyRecord` does in
[nulls](nulls.md).

The build holds this line, not review: [this repo's checks](../../README.md#checks) make an `Optional` constructor
parameter a compiler error.

The JDK has no `@Nullable`. If your project has none and doesn't want the dependency, split the constructor, or use a
named default with an `Optional` accessor (`Duration.ZERO` for an unknown length, read through `knownLength()`).
