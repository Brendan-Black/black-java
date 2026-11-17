# Java: `toString()`

`toString()` is the one method Java calls for you unasked: string concatenation, `String.valueOf`,
`StringBuilder.append`, `%s`, logging placeholders, and most template engines' `${...}`.

```java
record CorrelationId(String value) {
  @Override public String toString() { return value; }
}

log.info("[{}] order accepted", correlationId);   // prints the ID, not "CorrelationId[value=...]"
```

A record's generated `toString()` includes every field: override it on records that hold secrets or personal data.
