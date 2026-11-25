# Java: fail early

A record's compact constructor is the natural place:

```java
record EmailAddress(String value) implements TypedString {
  private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

  EmailAddress {
    if (value == null || !SHAPE.matcher(value).matches()) {
      // No echo of the value: it is personal data, and this message will end up in a log.
      throw new IllegalArgumentException("not an email address");
    }
  }

  @Override public String toString() { return "EmailAddress[redacted]"; }
}
```

Wrap with context and keep the cause:
`throw new OrderSyncException("syncing order " + orderId + " to warehouse " + warehouseId, e);`

Throwing is the default here: ["I don't know,
throw"](https://github.com/Brendan-Black/black/blob/master/philosophy/judgement.md). Where callers branch on bad
input, a static `parse` that returns a sealed result ([expected-failures](expected-failures.md)) is as good an answer.
