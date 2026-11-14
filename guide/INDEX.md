# Black for Java

What Java adds to the [black style guide](https://github.com/Brendan-Black/black) (`~/github/black/INDEX.md`). Each
file here sits at the same path as the rule it adds to; read it alongside that rule, only when the rule is in play.

| File | Adds |
|---|---|
| [design/least-interface](design/least-interface.md) | `Map` not `HashMap`; the identity example in Java |
| [design/restrictive-defaults](design/restrictive-defaults.md) | `final`, `List.copyOf`, records, package-private |
| [design/constructor-injection](design/constructor-injection.md) | `final` fields, no `@Autowired` on fields |
| [values/nulls](values/nulls.md) | `@Nullable` record field with an `Optional` accessor; Valhalla's `String!` |
| [values/no-optional-in-constructor](values/no-optional-in-constructor.md) | `Optional`; the compiler check that enforces it; no `@Nullable` in the JDK |
| [values/magic-strings](values/magic-strings.md) | enum vs `static final` constants class |
| [values/typed-strings](values/typed-strings.md) | a record with a compact constructor; `black.TypedString` |
| [values/typed-strings-cost](values/typed-strings-cost.md) | JDK 25 measurements |
| [values/value-classes](values/value-classes.md) | Java only: JEP 401 value records |
| [values/trusted-path](values/trusted-path.md) | a final class with `parse` and `trusted` |
| [values/debug-strings](values/debug-strings.md) | where Java calls `toString()` for you |
| [errors/fail-early](errors/fail-early.md) | `EmailAddress` compact constructor |
| [errors/expected-failures](errors/expected-failures.md) | sealed interface and an exhaustive `switch` |
| [errors/messages-for-callers](errors/messages-for-callers.md) | `ErrorCode` enum |
| [testing/every-bug-a-test](testing/every-bug-a-test.md) | `@ParameterizedTest` |
| [testing/self-contained](testing/self-contained.md) | `System.nanoTime()`, `Thread.sleep` |
| [testing/pseudos](testing/pseudos.md) | `PseudoHttpGateway` |

## Tools that hold the line

| Rule | Tool |
|---|---|
| Layout | [`java-formatter.xml`](../README.md#formatter), enforced by the `black.java-standards` Gradle plugin |
| Compiler warnings | [`java-warnings.prefs`](../README.md#warnings): the build fails where the editor warns |
| No `Optional` in a constructor | A compiler error, from [this repo's checks](../README.md#checks) |
| Nulls | NullAway (an Error Prone plugin) |
| Swallowed and overly broad catches | Error Prone; PMD's `EmptyCatchBlock` and `AvoidCatchingGenericException` |
| Exhaustive handling of outcomes | The compiler: a `switch` over a sealed type or enum |
| Duplicated literals | PMD's `AvoidDuplicateLiterals`, or Sonar's equivalent |
| Immutability | Error Prone's `@Immutable` check |
| Field injection, `Thread.sleep` in tests, package rules | ArchUnit |
| Injection holes | SpotBugs with Find Security Bugs; template engines that escape by default |
