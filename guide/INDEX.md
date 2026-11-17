# Black for Java

What Java adds to the [black style guide](https://github.com/Brendan-Black/black) (`~/github/black/INDEX.md`). Each
file here sits at the same path as the rule it adds to; read it alongside that rule, only when the rule is in play.

| File | Adds |
|---|---|
| [conventions/design/least-interface](conventions/design/least-interface.md) | `Map` not `HashMap`; the identity example in Java |
| [conventions/design/restrictive-defaults](conventions/design/restrictive-defaults.md) | `final`, `List.copyOf`, records, package-private |
| [conventions/design/constructor-injection](conventions/design/constructor-injection.md) | the constructor and `final` fields; no `@Autowired` on fields |
| [conventions/values/nulls](conventions/values/nulls.md) | `@Nullable` record field with an `Optional` accessor; Valhalla's `String!` |
| [correctness/values/no-optional-in-constructor](correctness/values/no-optional-in-constructor.md) | Java only: no `Optional` in a constructor, and the compiler check that enforces it |
| [conventions/values/magic-strings](conventions/values/magic-strings.md) | enum vs `static final` constants class |
| [conventions/values/typed-strings](conventions/values/typed-strings.md) | a record with a compact constructor; `black.TypedString` |
| [conventions/values/typed-strings-cost](conventions/values/typed-strings-cost.md) | JDK 25 measurements |
| [conventions/values/value-classes](conventions/values/value-classes.md) | Java only: JEP 401 value records |
| [conventions/values/trusted-path](conventions/values/trusted-path.md) | a final class with `parse` and `trusted` |
| [conventions/values/debug-strings](conventions/values/debug-strings.md) | where Java calls `toString()` for you |
| [conventions/errors/fail-early](conventions/errors/fail-early.md) | `EmailAddress` compact constructor |
| [conventions/errors/expected-failures](conventions/errors/expected-failures.md) | sealed interface and an exhaustive `switch` |
| [conventions/errors/messages-for-callers](conventions/errors/messages-for-callers.md) | `ErrorCode` enum |
| [philosophy/every-bug-a-test](philosophy/every-bug-a-test.md) | `@ParameterizedTest` |
| [correctness/testing/self-contained](correctness/testing/self-contained.md) | `System.nanoTime()`, `Thread.sleep` |
| [conventions/testing/pseudos](conventions/testing/pseudos.md) | `PseudoHttpGateway` |

## Tools that hold the line

| Rule | Tool |
|---|---|
| Layout | [`java-formatter.xml`](../README.md#formatter), enforced by the `black.java-standards` Gradle plugin |
| Compiler warnings | [`java-warnings.prefs`](../README.md#warnings): the build fails where the editor warns |
| No `Optional` in a constructor | A compiler error, from [this repo's checks](../README.md#checks) |
| Nulls | NullAway (an Error Prone plugin) |
| Swallowed and overly broad catches | Error Prone; PMD's `EmptyCatchBlock` and `AvoidCatchingGenericException` |
| Exhaustive handling of outcomes | The compiler: a `switch` over a sealed type or enum |
| Name magic strings | `checkMagicStrings`, in the Gradle plugin: a string value repeated in the main source set is an error if it is ever passed as an argument, and not reported otherwise; `javaStandards.magicStrings*`, a `package-info.java` or `module-info.java` (`@black.MagicStrings`) change that ([README](../README.md#magic-strings)) |
| Immutability | Error Prone's `@Immutable` check |
| Field injection, `Thread.sleep` in tests, package rules | ArchUnit |
| Injection holes | SpotBugs with Find Security Bugs; template engines that escape by default |
