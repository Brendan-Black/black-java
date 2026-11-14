# Java: every bug becomes a test case

```java
@ParameterizedTest
@ValueSource(strings = {
    "alice@example.com",
    "o'brien@example.com",
    "alice+tag@example.com",   // reported in production, 2026-03: plus-addressing was rejected
})
void acceptsValidAddresses(String input) {
  assertDoesNotThrow(() -> new EmailAddress(input));
}
```
