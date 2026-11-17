# Java: nulls

Convert incoming nulls to `Optional` or a default. Pass-through records are a convenient place:

```java
record MyRecord(@Nullable String prop1, String prop2) {
  Optional<String> optProp1() {
    return Optional.ofNullable(prop1());
  }
}
```

The null is announced (`@Nullable`) and readers get a way around it (the `Optional` accessor).

Help is coming from the language. Project Valhalla's value classes (JEP 401) and proposed null-restricted types
(`String!` never null, `String?` may be null) let the compiler and JVM track nullness directly, removing much of what
made null Tony Hoare's "billion-dollar mistake". Once your codebase is on a version that has them, prefer them to
annotations: a `!` is checked; a `@Nullable` is only a promise. Value classes are arriving first
([value-classes](value-classes.md)); null-restricted types are a separate, later JEP and not part of that preview.
