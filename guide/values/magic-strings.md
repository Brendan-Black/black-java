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
