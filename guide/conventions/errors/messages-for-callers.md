# Java: error codes

```java
enum ErrorCode {
  SHIP_WRONG_STATUS("E-9");

  private final String code;
  ErrorCode(String code) { this.code = code; }
  @Override public String toString() { return code; }
}

log.error("[{}] cannot ship order {}: status is {}, expected PAID",
    ErrorCode.SHIP_WRONG_STATUS, order.id(), order.status());
throw new UserFacingException(ErrorCode.SHIP_WRONG_STATUS, "This order was cancelled, so it can't be shipped.");
```
