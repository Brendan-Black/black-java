# Java: magic strings

```java
if (order.status().equals("SHIPPED")) { ... }
order.setStatus("SHIPED");   // compiles, runs, and is wrong forever
```

Closed set: `enum OrderStatus { PENDING, PAID, SHIPPED, CANCELLED }`. Open set: `static final` fields on a final,
non-instantiable class:

```java
final class Headers {
  static final String CORRELATION_ID = "X-Correlation-Id";
  static final String TENANT = "X-Tenant";

  private Headers() {}
}
```

A wire value handled in one place stays a literal: `case "GET /description.xml" ->`.

## The check, in Java

How the [repeated-literal
check](https://github.com/Brendan-Black/black/blob/master/reference/magic-strings-check.md)'s terms map to
Java. Settings are in the [README](../../../README.md#magic-strings).

- **Project:** a Gradle project's main source set.
- **Argument:** includes `List.of("a")` and the receiver of `"SHIPPED".equals(s)`.
- **Definition:** a `static final` field, or an enum constant's arguments.
- **Built from the literal:** `Optional.of("hard")`, or a record.
- **Proximity:** none (the default), a statement, a method, a file, a package, or the whole Java module, which reports
  nothing.
- **Module,** for whether a literal can name a constant: the package.
- **Generated:** files under the build directory, annotated `@Generated`, or opening with a comment line that starts
  `@generated`.
