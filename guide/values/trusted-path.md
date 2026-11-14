# Java: the trusted path

A record can't do this, because its canonical constructor can't be hidden, so you give up the generated `equals` and
`hashCode`:

```java
final class Sku implements TypedString {
  private static final Pattern SHAPE = Pattern.compile("[A-Z]{3}-\\d{6}");
  private final String value;

  private Sku(String value) { this.value = value; }

  /** Validates. The default for anything from outside. */
  static Sku parse(String raw) {
    if (raw == null || !SHAPE.matcher(raw).matches()) {
      throw new IllegalArgumentException("not a SKU: " + raw);
    }
    return new Sku(raw);
  }

  /** Skips validation. Only for values this system already validated, such as its own column. */
  static Sku trusted(String raw) { return new Sku(raw); }

  public String value() { return value; }
  @Override public String toString() { return value; }
  // equals and hashCode on value
}
```

Make `trusted` package-private where you can. The record is the default.
