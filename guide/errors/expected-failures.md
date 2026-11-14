# Java: expected outcomes

```java
sealed interface ChargeResult {
  record Charged(PaymentId id) implements ChargeResult {}
  record Declined(String reason) implements ChargeResult {}
}

switch (payments.charge(order)) {
  case Charged c  -> confirm(order, c.id());
  case Declined d -> notifyCustomer(order, d.reason());
}
```
