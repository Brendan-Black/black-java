# Java: value classes

**Convention.** Write typed-string wrappers so that adopting value classes is one word: a record or final class with
final fields, never used in `synchronized`, and never compared with `==`.

JEP 401 is integrated for JDK 28 as a preview feature, behind `--enable-preview`, and the syntax may still change:

```java
value record Sku(String value) implements TypedString { ... }
```

A value object has no identity, which frees the JVM to skip the allocation or lay the object out flat. `==` on value
objects compares state field by field, so two wrappers around equal but distinct `String`s are still not `==`; `equals`
stays the comparison.

What it buys: constructors still run, so validation stands. Flattening is something the JVM may do, not a promise, and
it depends on size: flattened data must be readable and writable atomically, which today means about 64 bits including
the null flag. Value classes are likely to retire the raw-form rule in [typed-strings-cost](typed-strings-cost.md), but
measure the wrapper you have before taking that storage apart.
