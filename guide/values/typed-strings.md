# Java: typed strings

A record whose compact constructor enforces the shape:

```java
record Sku(String value) implements TypedString {
  private static final Pattern SHAPE = Pattern.compile("[A-Z]{3}-\\d{6}");

  Sku {
    if (value == null || !SHAPE.matcher(value).matches()) {
      throw new IllegalArgumentException("not a SKU: " + value);
    }
  }

  @Override public String toString() { return value; }
}

void reserve(Sku sku, WarehouseId warehouse, CustomerId customer) { ... }
```

The marker is published, with nothing in it, as `black.TypedString` in this repo's [`lib` module](../../README.md#library),
so projects share one definition. If you also want one Jackson serializer or JPA `AttributeConverter` to handle them
all, give it a `String value()` method; a record whose component is named `value` implements it for free.
