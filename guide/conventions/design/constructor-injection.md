# Java: pass dependencies in

Through the constructor, into `final` fields. Not field injection (`@Autowired` on a private field), not setters, and
not a static or a service locator the class reaches for.

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

`new OrderShipper(pseudoWarehouse, fixedClock)` is the whole test setup. A DI framework may do the wiring in
production, but only through the constructor, so the class never needs the framework to be built.
