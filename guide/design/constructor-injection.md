# Java: inject through constructors

Collaborators in `final` fields, set by the constructor. Not field injection (`@Autowired` on a private field), and not
setters.

```java
final class OrderShipper {
  private final Warehouse warehouse;
  private final Clock clock;

  OrderShipper(Warehouse warehouse, Clock clock) {
    this.warehouse = warehouse;
    this.clock = clock;
  }
}
```

`new OrderShipper(pseudoWarehouse, fixedClock)` is the whole test setup.
