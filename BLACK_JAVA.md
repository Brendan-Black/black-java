# Black Java: a style guide

## Ansatz

This guide assumes you already know Dependency Injection (DI), its strengths and its limits, and builds on that
baseline.

Here, "interface" means more than the Java keyword: it is anything that is not a final/worker class. An abstract
class, a parent class, a wire contract, and so on.

Layout (indentation, braces, line length, import order) is not covered. Use a formatter, such as
[this repo's](README.md), enforce it in the build, and spend the argument on something else.

## At a glance

Each rule in one line, with its main exception. *Convention* yields to the code you are in; *correctness* does not
(see *"Correctness vs convention"*).

| Rule | Kind | Main exception |
|---|---|---|
| [Follow the code you are in](#consistency-follow-the-code-you-are-in) | Principle | Correctness rules don't yield |
| [Make the type say it](#make-the-type-say-it) | Principle | Not every value earns a type |
| [Interfaces at seams, not everywhere](#program-to-an-interface-not-an-implementation) | Convention | None needed where nothing is swapped or promised |
| [Don't build abstractions nobody uses](#do-not-build-abstractions-nobody-uses) | Convention | Marker interfaces, whose user is the reader |
| [Declare the least interface, keep the identity](#code-against-the-least-interface) | Convention | You need a concrete type's guarantee |
| [Don't chain interfaces that aren't definitional](#interfaces-do-not-chain-them) | Convention | Chain when the child can't stand alone |
| [Final, immutable, package-private by default](#defaults-final-immutable-package-private) | Convention | Loosen when a real reason appears |
| [Inject through constructors](#inject-through-constructors) | Convention | Genuinely optional dependencies |
| [Nulls don't cross boundaries you own](#nulls-wrap-them) | Convention | An announced `@Nullable` record field; private helpers |
| [No `Optional` in a constructor](#no-optional-in-a-constructor) | Correctness | None |
| [Name magic strings](#magic-strings-name-them) | Convention | Used once, handled in one place, prose, test data |
| [Wrap typed strings](#typed-strings-wrap-them) | Convention | Free text; strings that never leave a method; strings you built from a template |
| [`toString()` is debug output](#tostring-for-people-not-pipelines) | Convention | Small, context-free value wrappers |
| [No HTML, SQL, or shell from `toString()` or concatenation](#never-use-tostring-to-generate-html) | Correctness | A fixed template with every slot escaped |
| [No secrets or personal data in logs](#smaller-points) | Correctness | None |
| [Don't swallow exceptions](#dont-swallow-exceptions) | Correctness | Catch to retry, fall back, or translate |
| [Expected outcomes go in the return type](#expected-failures-are-part-of-the-contract) | Convention | Only the outcomes callers branch on |
| [Errors the reader can't trace get a code](#write-error-messages-for-the-person-calling-in) | Convention | A unique message is enough when the reader has the source |
| [Leave nothing half-done](#leave-nothing-half-done) | Correctness | None |
| [New tests are self-contained](#what-self-contained-looks-like) | Correctness | Labelled smoke tests outside the merge gate |
| [Every bug becomes one more test case](#the-goal-every-bug-becomes-one-more-test-case) | Principle | None |
| [Pseudos are strict and inspectable](#pseudo-implementations-must-be-inspectable) | Convention | None |

# Part I: Philosophy

## Consistency: follow the code you are in

> **"When in Rome, do as the Romans do"** - Saint Ambrose
>
> **"The concept of the road less travelled by does not apply when driving in the slush."** - Brendan Black

Everything in this guide is a default, not an override. Where the code you are in already has an established way of
doing something, follow it, even if this guide would differ. One consistent convention, even a mediocre one, is easier
to read, search, and change than a codebase where every file reflects whoever last touched it.

Breaking convention costs everyone who comes after you. Every reader must work out whether the difference means
anything (it usually doesn't), every search must cover both patterns, and every change must pick one. The worn path
has traction; striking out on your own is how you end up in the ditch.

In practice:

- **Match the surrounding code.** Naming, error handling, null handling, test structure, file layout.
- **Consistency is scoped.** The file first, then the module, then the codebase. A new module with no history is where
  this guide applies in full.
- **Change conventions deliberately.** If a local convention is harmful, change it everywhere, as its own change, with
  agreement from the code's owners, not in the one file you happen to be in. A half-finished migration is worse than
  either the old way or the new one.
- **Correctness is not a convention.** You don't copy a swallowed exception or a race condition because the code next
  door has one. See below.

### Correctness vs convention

Most of this guide is convention, and yields to whatever the surrounding code does. A handful is correctness, and
doesn't: **if following the local pattern would make your code wrong, insecure, or flaky, it gets no deference.**

**Convention (follow the local pattern):**

- Where interfaces go, and whether a service gets an `Impl` (*"Program to an interface"*)
- How narrowly collaborators are declared (*"Code against the least interface"*)
- Whether interfaces are chained (*"Interfaces: do not chain them"*)
- How final, mutable, and visible classes are by default, and how dependencies are injected
- How nulls are represented: `Optional` return types, annotations, defaults (*"Nulls: wrap them"*)
- How constants are named and grouped (*"Magic strings: name them"*)
- Which shaped strings get their own type, and how they're mapped at the edges (*"Typed strings: wrap them"*)
- What `toString()` prints for debugging
- Result types vs exceptions for expected failures, and the error-code scheme
- Test structure, naming, and layout

**Correctness (do it right regardless):**

- **Don't swallow exceptions.** Copying `catch (Exception e) { return null; }` copies the bug.
- **Don't generate HTML (or SQL, or shell) from `toString()` or string concatenation.** That's an injection hole, not
  a style choice.
- **Don't log secrets or personal data**, directly or through a generated `toString()`.
- **No `Optional` in a constructor.** An `Optional<Anything>` constructor parameter, record component included, is
  a three-state value (present, empty, `null`) pretending to be two. No local habit makes that safe.
- **Leave nothing half-done.** A non-atomic write is a data-corruption bug however common it is nearby.
- **New tests are self-contained.** A test that passes only when something else happens to be running is flaky, and
  flakiness is a defect. Write *your* new tests this way even if the neighbours aren't, but converting the existing
  suite is its own piece of work (see *"Change conventions deliberately"*). If you can't write one that way, see
  *"If you can't test it, fix it"*.

Where the line is unclear, ask whether a reviewer seeing the local pattern for the first time would call it a bug. If
so, it's correctness.

## Make the type say it

One idea runs through most of this guide: **put what you know about a value into its type, so the compiler carries
it instead of people's memory.**

A string that must look like a SKU, a field that may be absent, a status from a fixed set, a charge that can be
declined: each of these is knowledge. It either lives in the type, where the compiler checks it at every use, or in
someone's head, where it is checked whenever they happen to remember. Most of Parts III and IV apply that one idea:

- **Nulls:** absence is announced in the signature, not discovered at runtime (*"Nulls: wrap them"*).
- **Magic strings:** a fixed set of values becomes an enum (*"Magic strings: name them"*).
- **Typed strings:** a value with a shape becomes a type that enforces it (*"Typed strings: wrap them"*).
- **Validation:** an invalid value is never constructed, so holding one is proof (*"Fail early, fail loudly"*).
- **Expected failures:** outcomes a caller must handle become a sealed type the compiler makes them handle
  (*"Expected failures are part of the contract"*).

The question to ask of any value: **could a caller get this wrong without the compiler noticing?** If so, can the type
say it instead? Don't push it past usefulness, though. A type for every string is the same mistake as an interface
for every class; each section says where it stops paying.

## Let tools hold the line

Rules enforced by the build survive; rules that live only in prose erode one review at a time. Where a tool can check
a rule, wire it into the build and let it fail, so reviewers can spend their attention on what tools can't see.

| Rule | Tool |
|---|---|
| Layout | [This repo's formatter profile](README.md), enforced by the `black.java-standards` Gradle plugin |
| Nulls | NullAway (an Error Prone plugin) |
| Swallowed and overly broad catches | Error Prone; PMD's `EmptyCatchBlock` and `AvoidCatchingGenericException` |
| Exhaustive handling of outcomes | The compiler: a `switch` over a sealed type or enum |
| Duplicated literals | PMD's `AvoidDuplicateLiterals`, or Sonar's equivalent |
| Immutability | Error Prone's `@Immutable` check |
| `Optional` in a constructor | A compiler error, from [this repo's checks](README.md#checks) |
| Field injection, `Thread.sleep` in tests, package rules | ArchUnit |
| Injection holes | SpotBugs with Find Security Bugs; template engines that escape by default |

What no tool catches well: whether an interface sits at a real seam, whether a signature has lost the identity of its
argument, whether a `toString()` is context-free, what ends up in a log, and whether a pseudo is strict. That is what
review is for.

# Part II: Design

## "Program to an interface, not an implementation"

The first rule of Black Java is the original object-oriented designers' advice, applied where it pays: interfaces go
at the **seams** of your system, not in front of every class.

A *seam* is where you need to swap one implementation for another, or where you promise something to code you don't
control. A class gets an interface when at least one of these holds:

- **It touches the outside world.** Network, database, clock, randomness, environment. *Hermetic Testing* replaces
  these with pseudos, and a pseudo needs an interface to stand behind. `HttpGateway`, `UserRepository`, `Clock`. The
  local disk usually doesn't count: a temporary folder the test creates is the real thing, brought along, and needs no
  interface. Add one only when a test needs something a real folder can't do, such as a disk that fills up part way.
- **It is a contract across a module or team boundary.** If someone else compiles against it, write down what you
  promise, separately from what you happen to do today.
- **It has, or clearly will have, more than one implementation.** Strategies, policies, pluggable formatters. "Clearly
  will" means you can name the second implementation, not that you can imagine one.

Everything else can just be a class. A service that coordinates two repositories and applies some rules doesn't need
a `FooService` and a `FooServiceImpl`. Wire the real thing and test the real thing, with pseudos only at the
boundaries beneath it. Records and other value types rarely need one; they *are* the contract.

### Why bother at the seams

**An interface is where a promise gets written down.** Callers can come to depend on every public method, intended or
not, and at a seam whatever they depend on becomes the contract. An interface separates what you promise from what you
happen to do today.

**Write it before the callers, not after.** Extract-interface is cheap, but its result tends to be the class's surface
(its setters, its lifecycle hooks, the convenience method one caller needed) rather than a designed contract. When you
see a seam coming, write the interface first, shaped by what callers need.

**Seams make hermetic tests possible.** An interface over the payment provider is what lets a strict pseudo stand in.
Without seams, you test against production or not at all.

### Do not build abstractions nobody uses

Interfaces are important; generality is not. **An abstraction nobody uses is useless by definition**, and worse than
useless: every reader must understand it, every change must respect it, and it looks like a promise the code isn't
making.

The usual offenders:

- An interface with one implementation and no test that substitutes it, "in case we need to swap it later."
- A type parameter only ever instantiated with one type.
- A strategy, hook, or plugin point with exactly one strategy.
- A config flag or overload no caller passes a different value to.
- A base class extracted from one subclass.

Each is a bet on a future that usually doesn't arrive, and rarely looks the way you guessed when it does. The second
implementation tells you what the abstraction should be; build it then.

### When in doubt

Start with a class. When a real seam appears (a second implementation, a test that needs a pseudo, a consumer in
another module), extract the interface, designed from the caller's side rather than copied from the class's public
methods. Indirection costs every reader a hop through "go to definition", so make sure something pays it back.

## Code against the least interface

Obvious right up until it isn't. For example:

1. Declare `Map`, not `HashMap`, unless you depend on something only the concrete type gives you (such as the
   iteration order of a `LinkedHashMap`).
1. Declare collaborators as the role, not the worker filling it: `EmailSender`, not `SmtpEmailSender`.

In short: declare variables and injected dependencies as the minimal sensible shape the task needs.

### An important distinction

It is easy to misread this as "code against only what is used". That is wrong. Take this proper code:

```java
// Extant interfaces
interface HasId { Object id(); }
interface CacheKey<T> extends HasId { }

// Proper implementation
void invalidateUser(CacheKey<User> key) {
  userCache.evict(key.id());
}
```

Coding against only what the body calls would tempt you into this:

```java
void invalidateUser(HasId key) {
  userCache.evict(key.id());
}

// or, more frustratingly...
void invalidateUser(CacheKey<?> key) {
  userCache.evict(key.id());
}
```

Both compile and run, since the body only calls `key.id()`. But the identity of the thing is lost, and whoever comes
after you is left guessing at the intent. The method was never an operation on *keys*; it is an operation on *user
keys*.

**Narrow the capabilities you require, not the identity of the thing you operate on.** The test is not "does it still
compile?" but **"would this method correctly handle every value its signature accepts, in the context of the
surrounding code?"**

## Interfaces: do not chain them

Don't bake one layer's concern into another layer's types. The most common way to do it is a *chained interface*: one
bound into an inheritance tree it doesn't need in order to make sense on its own.

```java
interface CacheableObject {
  Object getCacheKey();
}

interface User extends CacheableObject {
  @Override
  UserId getCacheKey();
  String getUsername();
}
```

A seasoned developer might call that textbook inheritance. It isn't wrong today; it paints you into a corner. A user
is not *definitionally* cacheable. Caching is the cache layer's concern, and baking it into `User` means a user can't
travel anywhere without the cache coming along. The day your boss asks for a module that shares users across services
without the cache, you split `User` into a `UserConcept` and a leftover `User extends CacheableObject, UserConcept`,
and every further concern multiplies the combinations. Better not to play the game at all:

```java
// A seam: tests stand in a PseudoUser.
interface User {
  String username();
}

// The cache layer knows about users. Users need to know nothing about the cache.
Cache<UserId, User> userCache;
```

This isn't speculative generality (see *"Do not build abstractions nobody uses"*). Declining to chain builds nothing
and costs nothing; you aren't betting on a future, only declining to close one off. In a legacy codebase, the best you
can often do is an empty interface that unions the two types.

Chaining is right where the extending interface makes no sense standing alone:

```java
interface Cell {
  int getAge();
}

interface Eukaryote extends Cell {
  Mitochondria getPowerhouseOfTheCell();
}
```

When the relationship is definitional, chain. When it **is not**, take a second look.

## Defaults: final, immutable, package-private

Start every class at the most restrictive setting, and loosen it only when a reason appears.

- **Final.** A class not designed for extension shouldn't allow it; a subclass of a class not built for one is coupled
  to its internals. This is the "worker class" of the Ansatz: the concrete, final thing behind a seam. When something
  genuinely needs to extend it, open it up and document what subclasses may rely on.
- **Immutable.** Fields `final`, collections copied on the way in (`List.copyOf`) and never handed out mutable.
  Records do most of this for you. An immutable value can be shared across threads, cached, and used as a map key
  without anyone having to think. Mutable state is fine where it's the point (a builder, an accumulator inside a
  method, the recorder in a pseudo); keep it local and don't let it escape.
- **Package-private.** `public` is a promise to every caller in every module (see *"Why bother at the seams"*). Make
  public what other packages need and nothing else. Package-private is also how you fence off escape hatches such as
  `trusted` in *"Skipping the check on a trusted path"*.

## Inject through constructors

Pass collaborators in through the constructor and keep them in `final` fields. Not field injection (`@Autowired` on a
private field), and not setters.

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

- **Dependencies are visible.** The constructor lists everything the class needs. A constructor with nine parameters
  is a class doing too much; field injection hides that.
- **Tests need no framework.** `new OrderShipper(pseudoWarehouse, fixedClock)` is the whole setup.
- **The object is never half-built.** It can't exist without its dependencies, so the fields can be `final`.

Setter injection is for genuinely optional dependencies, which are rarer than they look: most turn out to be a default
value in disguise.

# Part III: Values

Each section in this part applies *"Make the type say it"* to a different kind of value.

## Nulls: wrap them

Null, as it happens, is a 4-letter word.

Don't let null cross a boundary you own. A boundary is anything non-private. Where null comes in from the edge (a
library, a framework, a column, a caller), convert it to an `Optional` or a sensible default, and keep it out of your
non-private return types. A local that is briefly null inside a method hurts no one, and neither does a private helper
that returns null to the method beside it; a null that travels does. Wrapping those helpers buys nothing: an
`isEmpty()` followed by a `get()` is the same check with more typing. The records you (should) use as pass-through data
carriers are a convenient place to do this:

```java
record MyRecord(@Nullable String prop1, String prop2) {
  Optional<String> optProp1() {
    return Optional.ofNullable(prop1());
  }
}
```

A nullable record field is fine as long as it says so; sometimes null genuinely is the value someone wants to pass.
It's safe because the null is announced (`@Nullable`) and readers get a way around it (the `Optional` accessor).

### No `Optional` in a constructor

This one is a hard rule, not a convention: **`Optional<Anything>` never appears in a constructor's parameter list.**
That includes a record's components, since they are its canonical constructor. No exception, and no deference to the
code around you (see *"Correctness vs convention"*).

`Optional` is a return-type tool. As a parameter it is one more thing that can itself be null, so the "optional" value
has three states, and the type only admits to two. Picture it at scale: `new Address(Optional.empty(),
Optional.empty(), Optional.of(city), Optional.empty())` at every construction site, every fixture and mapper wrapping
and unwrapping, the ceremony burying the data, and still nothing stopping a bare `null` passed for one of the
`Optional`s.

Take the plain value, announced `@Nullable`, and hand out the `Optional` from an accessor, as `MyRecord` does above.

The build holds this line, not review: [this repo's checks](README.md#checks) make an `Optional` constructor parameter a
compiler error.

The JDK has no `@Nullable`. If your project has none and doesn't want the dependency, split the constructor where you
can (one with the dependency, a named factory without it), or use a named default with an `Optional` accessor
(`Duration.ZERO` for an unknown length, read through `knownLength()`). A default is a weaker promise than an annotation,
so keep the accessor next to it.

Help is coming from the language. Project Valhalla's value classes (JEP 401) and proposed null-restricted types
(`String!` never null, `String?` may be null) let the compiler and JVM track nullness directly, removing much of what
made null Tony Hoare's "billion-dollar mistake". Once your codebase is on a version that has them, prefer them to
annotations: a `!` is checked; a `@Nullable` is only a promise. Value classes are arriving first (see *"Value
classes"*); null-restricted types are a separate, later JEP and are not part of that preview.

## Magic strings: name them

A *magic string* is a literal that carries meaning: a status, a header name, a config key, a JSON field, a role, an
event type. If one appears more than once, name it: an enum, or a constant.

```java
// before
if (order.status().equals("SHIPPED")) { ... }
// ...elsewhere...
order.setStatus("SHIPED");   // compiles, runs, and is wrong forever
```

The compiler checks your identifiers, not your strings. A typo in a literal isn't a compile error, isn't a test
failure unless you tested that exact path, and isn't a search hit when someone looks for every place that handles
shipped orders. Naming it fixes all three: typos fail to compile, "find usages" finds every use, and a rename changes
them all.

### Enum or constant?

**If the set is closed and the value travels, use an enum.** Statuses, roles, event types, error codes: anything whose
legal values you could list today, and which is stored, passed around, or switched on in more than one place. An enum
makes illegal values unrepresentable and works with an exhaustive `switch` (see *"Expected failures are part of the
contract"*).

```java
enum OrderStatus { PENDING, PAID, SHIPPED, CANCELLED }
```

**If the set is open, use constants.** Header names, config keys, metric names, query parameter names: fixed strings,
but not a set you would switch on. Make them `static final` fields on a final, non-instantiable class:

```java
final class Headers {
  static final String CORRELATION_ID = "X-Correlation-Id";
  static final String TENANT = "X-Tenant";

  private Headers() {}
}
```

Group constants by concept, near the code that owns the concept: `Headers`, `ConfigKeys`, `MetricNames`. A
project-wide `Constants` class becomes a junk drawer every module depends on.

### What doesn't count

- **Strings used once.** A single-use literal is already as findable as it gets. Name it at the second use, or
  earlier if that reads better.
- **Wire values handled in one place.** A protocol's action names, dispatched by one `switch` whose `default` rejects
  the rest, can stay literals. Input from outside needs that `default` anyway, so an enum adds a lookup and buys no
  exhaustiveness. `case "GET /description.xml" ->` also says what it matches, which a concatenation of constants
  doesn't.
- **Prose.** Log messages, exception messages, and UI copy are text for people, not values the code branches on. The
  exception is anything embedded in them that *is* a value, such as an error code.
- **Test data.** `"John Smith"` in a fixture is data, not a contract. Expected values that mirror a production
  constant should reference it, though, or the test keeps passing after the production value changes.

## Typed strings: wrap them

Magic strings are fixed values. *Typed strings* are the other half of the problem: values that vary but have a shape.
An email address, a currency code, a SKU, a tenant ID, a version number. The code expects each to match a pattern, but
the type says `String`, and a `String` accepts anything.

```java
void reserve(String sku, String warehouseId, String customerId) { ... }

reserve(customerId, sku, warehouseId);   // compiles; three strings are three strings
```

The shape exists; it just isn't written down. It lives in a regex somewhere, or three slightly different regexes, or
only in the heads of whoever wrote the code. Every method that receives the string must re-validate it, trust that
someone upstream did, or not think about it at all, and it is usually the last. This is a large, quiet source of bugs:
a value that was never the right shape travels deep into the system before anything notices.

Write the shape down as a type. A record whose compact constructor enforces it turns the unwritten rule into
something the compiler carries for you:

```java
interface TypedString {}

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

What that buys you:

- **Validation happens once, at the edge.** Convert the raw string where it enters (a request, a file, a column);
  from then on, holding a `Sku` is proof it is valid. This is *"Fail early, fail loudly"* applied to every value with
  a shape.
- **Mix-ups stop compiling.** Swapped arguments are a type error, not a production incident.
- **The rule has one home.** The pattern lives in one place, "find usages" on `Sku` shows everywhere SKUs flow, and a
  format change is one edit.
- **Signatures explain themselves.** `reserve(Sku, WarehouseId, CustomerId)` needs no javadoc to say what the strings
  are.

These are the small value wrappers that *"`toString()`: for people, not pipelines"* lets return their canonical form.

`TypedString` is a marker, and a marker is not an unused abstraction: its user is the reader. It says this type exists
to hold and validate one shaped value, and "find implementations" on it gives you a catalogue of every shaped value in
the system. If you also want one Jackson serializer or JPA `AttributeConverter` to handle them all, give it a
`String value()` method; a record whose component is named `value` implements it for free.

This repo publishes the marker, with nothing in it, as `black.TypedString`, in its [`lib` module](README.md#library), so projects can share
one definition.

### On hot paths

The wrapper is not the cost; the check is. Measured on JDK 25, a wrapper is 16 bytes and about 5 ns when stored, and
nothing at all when it doesn't escape the method, because the JIT removes it. A precompiled pattern against a short
string takes nanoseconds but allocates a `Matcher` (136 bytes) every call, and the JIT did not remove that. A
hand-written loop over the same string allocated nothing.

- **Keep the wrapper.** Passing one around costs nothing, so hot code is no reason to go back to `String`.
- **Validate once, at the edge.** Never re-validate something already typed. A record's `parse` can't avoid running the
  check twice, once itself and once in the canonical constructor, so that check needs to be cheap.
- **Choose the check by how often it runs.** A regex is fine for startup and config. Per request or in a loop, write
  the character loop.
- **Never re-derive from the string.** A type that needs its parsed form holds it or uses a cheap test. A `URI.create`
  inside `isLocal()` measured about 50 times the cost of checking the prefix.
- **Save `trusted` for checks that stay expensive by hand**, such as parsing URIs or dates in bulk (see *"Skipping the
  check on a trusted path"*).
- **For millions of values held in memory, store the raw form.** Keep a `long[]` or `String[]` inside one class and
  return the wrapper from the accessor. Ten million wrappers is 160 MB, and ten million more objects for the GC to
  trace.

### Value classes

Typed strings are the kind of small, immutable wrapper that value classes exist for. JEP 401 is integrated for JDK 28
as a preview feature, behind `--enable-preview`, and the syntax may still change:

```java
value record Sku(String value) implements TypedString { ... }
```

A value object has no identity, which frees the JVM to skip the allocation or lay the object out flat. Write wrappers
now so that adopting this is one word: a record or final class with final fields, never used in `synchronized`, and
never compared with `==`. `==` on value objects compares state field by field, so two wrappers around equal but
distinct `String`s are still not `==`; `equals` stays the comparison.

Be clear about what this buys. Constructors still run, so the rules on validation above stand. Flattening is something
the JVM may do, not a promise, and it depends on size: flattened data must be readable and writable atomically, which
today means about 64 bits including the null flag. Value classes are likely to retire the raw-form rule above, but
measure the wrapper you have before taking that storage apart.

### Skipping the check on a trusted path

On a genuinely hot path, such as bulk-loading millions of rows your own system already validated, a check that stays
expensive may be worth skipping. Don't do that by making validation opt-in: a `new Sku(raw).validate()` that callers
can forget means holding a `Sku` proves nothing again. Make validation the default and skipping it the explicit, named
exception:

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

A record can't do this, because its canonical constructor can't be hidden, so you give up the generated `equals` and
`hashCode`. In exchange, "find usages" on `trusted` lists every place that assumed validity, and making it
package-private limits who can. Be honest about "already validated", too: a migration, a manual fix, or another
service writing the same column can each break that assumption. Reach for this only when profiling says so; the
record is the default.

### When not to bother

- **Free text.** Names, descriptions, comments, search queries. They have no shape to enforce, and inventing one
  (names must be letters only) rejects real people's real data.
- **Strings that never leave a method.** A value parsed and consumed within a few lines gains little from a type.
- **Strings your own code builds from a template.** If a value is assembled in one place from inputs that are already
  trusted, a shape check can only confirm the line above it. Leave it a `String`, unless the type also does something a
  `String` can't, such as building URLs or redacting itself in `toString()`.

Keep the check to the *shape*. "Is this a well-formed SKU?" belongs in the constructor; "does this SKU exist?" needs
a database and belongs in a service.

Opaque tokens (session tokens, API keys, pagination cursors) are the opposite case: they need a type *because* you
must not look inside them. There is no shape to validate; the wrapper's job is to make the value impossible to
mistake for a string you can parse, log, or compare by hand. Expose nothing but what the token is for, and give it a
redacted `toString()` (see *"Mind what generated `toString()`s print"*).

## `toString()`: for people, not pipelines

`toString()` is the one method Java calls for you unasked: string concatenation, `String.valueOf`,
`StringBuilder.append`, `%s`, logging placeholders, and most template engines' `${...}`. That is its convenience and
its danger: every implicit call is invisible to "find usages", the same problem *"Magic strings: name them"* solves.

By default, `toString()` is **debug output for a developer**: what the debugger, a failed assertion, or a stray log
line shows. Make it useful to that reader, and don't parse it, persist it, compare against it, or depend on its
format.

### The exception: small value wrappers

In one case the implicit call is the point. A thin wrapper whose identity *is* a string may return that string from
`toString()`, as its **canonical, context-free form**. Context-free means correct wherever it lands: nothing in it
needs escaping, and concatenating it with anything does no harm. `ErrorCode` in *"Write error messages for the person
calling in"* is the model: `"E-9"` reads the same in a log line, an exception, and a support ticket.

```java
record CorrelationId(String value) {
  @Override public String toString() { return value; }
}

log.info("[{}] order accepted", correlationId);   // prints the ID, not "CorrelationId[value=...]"
```

Keep these wrappers small and their string form boring. If you want the output to vary by destination, it isn't
context-free, and it shouldn't be `toString()`.

### Never use `toString()` to generate HTML

The one firm line in this section, because the failure is a security hole, not a style problem.

- **Escaping depends on context.** Element text, attributes, URLs, inline script, and CSS each escape differently.
  `toString()` takes no arguments, so it can't know which one it is producing.
- **The safety lives in the type, and the type evaporates.** A `SafeHtml` that promises escaped contents stops
  promising once concatenated: `safeHtml + userInput` is a plain `String` holding unescaped input, and nothing
  downstream can tell.
- **It already has a job.** The debugger, the logs, and every assertion message call `toString()` too. Either your
  page renders debug text or your debugger shows markup.

Render HTML through the template engine's escaping, or an explicit, searchable method such as `toHtml()` or
`render(HtmlContext)`. GWT's `SafeHtml` chose `asString()` over `toString()` for exactly these reasons. The same
applies, more gently, to any format that needs escaping (SQL, JSON, CSV, shell commands): use its builder or library.

This line is for dynamic markup, where the structure depends on the data or the slots are many. A fixed document with a
few slots, each escaped where it is filled, is fine: it reads like what it produces, and a builder would only hide that.
SQL and shell get no such allowance; bind parameters and argument lists cost nothing.

### Smaller points

- **Mind what generated `toString()`s print.** A record's includes every field, so one holding a password, token, or
  personal data will cheerfully log it. Override it for those records, or keep secrets in a wrapper with a redacted
  `toString()`.
- **Don't assert on it.** A test that compares `toString()` output breaks when someone improves the debug format, and
  passes when the fields it should check are wrong. Assert on the fields.

# Part IV: Error Oriented Programming

Failing correctly is arguably more important than succeeding correctly.

> Failure is ALWAYS an option. (Credit: Mythbusters)

Look at how each path gets exercised. The success path runs thousands of times a day; if it's wrong, someone notices
by lunch. The failure path runs rarely, usually under load, at the worst possible moment, and often for the first time
in production. And the costs differ: a loud failure costs a retry and a bug report, while a quiet wrong answer (a
swallowed exception, a default that shouldn't have been returned, a half-written record) costs whatever was built on
it before anyone noticed.

So design the failure path as a first-class concern, not as the `catch` block you fill in at the end.

## Fail early, fail loudly

Validate where data enters, and refuse to construct anything invalid. A record's compact constructor is the natural
place:

```java
record EmailAddress(String value) implements TypedString {
  private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

  EmailAddress {
    if (value == null || !SHAPE.matcher(value).matches()) {
      // No echo of the value: it is personal data, and this message will end up in a log.
      throw new IllegalArgumentException("not an email address");
    }
  }

  @Override public String toString() { return "EmailAddress[redacted]"; }
}
```

Once an `EmailAddress` exists, nothing downstream has to wonder; this is *"Typed strings: wrap them"* doing its job.
It's the same instinct as *"Pseudos are strict"*: an unexpected situation should become an error immediately, while
the cause is still visible, not three layers later.

## Don't swallow exceptions

One of the most damaging patterns in Java:

```java
try {
  return repository.find(id);
} catch (Exception e) {
  log.warn("lookup failed", e);
  return null;
}
```

It turns an infrastructure outage into "user not found", hands the caller a null, and leaves a warning nobody reads.
Catch an exception only if you can do something about it: retry, fall back to something genuinely equivalent, or
translate it into a domain outcome. Otherwise let it propagate. When you do wrap, add context and keep the cause:

```java
throw new OrderSyncException("syncing order " + orderId + " to warehouse " + warehouseId, e);
```

A designed fallback isn't a swallow, and it doesn't need a log line. If a picture can't be read and the icon is the
intended answer, a comment saying so is enough. Logging every fallback makes noise, and then code to suppress the noise.
Log when someone needs to find out; don't log just to show the `catch` isn't empty.

## Expected failures are part of the contract

Not every failure is exceptional. "Payment declined" and "username taken" are ordinary outcomes callers must handle,
and a sealed interface puts them in the return type, where the compiler makes sure they do:

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

The `switch` is exhaustive, so a new outcome is a compile error at every call site, not a surprise at runtime.

`reason` is a `String` here because it is only passed along to the customer. Once a caller branches on it (retry on
`"insufficient funds"`, flag on `"suspected fraud"`), it is a value the code depends on, and it should become an enum
(see *"Magic strings: name them"*).

Rule of thumb: **expected outcomes go in the return type; bugs and infrastructure failures are exceptions.** Don't
overdo it. Wrapping every call in a result type buries the success path, and an exception hierarchy nobody catches is
just another unused abstraction. Model the failures callers actually branch on.

## Write error messages for the person debugging

Say what was being attempted, with which input, and what was expected instead. `"Invalid state"` tells them nothing;
`"cannot ship order 4471: status is CANCELLED, expected PAID"` tells them where to look.

## Write error messages for the person calling in

User-facing errors come without a stack trace. When you're called at odd hours, you usually hear "it's just not
working" and nothing more. Write errors you can find in the code from whatever the user tells you.

**Give an error a code when its reader can't trace it or will relay it secondhand.** "E-9". Customers, support staff,
anyone without the source. It survives translation and paraphrase, and users read numbers back accurately. A code must
be unique to its throw site; a shared code is worse than none.

**Where the reader has the source, a unique message is enough.** It is already the search term, and a code only adds a
table to maintain. The same goes for a usage error that explains itself on the spot, and for a console log read by the
person debugging.

**Give it unique wording too, where you can.** Users remember sentences better than numbers, and a sentence no other
error uses ("This order was cancelled, so it can't be shipped", not "Something went wrong") gives them a second chance
to report it accurately. Aim for it, but don't let it block you. Wording no other error uses or resembles is a
demanding standard, and a real burden for those of us who aren't very creative; the code already makes the error
findable.

> **A side note on that constraint.** If even rough uniqueness is becoming impossible, notice it. A codebase where it
> works is small and focused, with fewer places for things to go wrong. Treat the difficulty as positive pressure:
> keep the scope tight, or split a sprawling module into pieces small enough to name their own failures.

**Deliberate misspelling** ("Retreval Error") is a last resort. It is trivially searchable, but the first person,
spellchecker, or AI cleanup to "fix" the typo silently breaks the search, and it can look unprofessional, depending on
your client.

**The code joins the user's report to your logs.** The user sees the short message and the code; the log gets the
full, debugging-grade message with the *same* code. One search finds both the throw site and the log entry with its
context, and internals (SQL, stack traces, IDs) stay out of the user's view.

An error code is exactly what *"Magic strings: name them"* is about, so give the codes an enum used by both the log
and the exception. Searching for "E-9" lands on the constant, and "find usages" from there lands on every place it is
thrown and logged:

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

When the call comes at 3am, the code and the wording turn "it's not working" into a single search.

## Leave nothing half-done

A failure partway through an operation should leave the system as it was, not somewhere in between. Use transactions
where you have them; where you don't, build the complete result first and commit it in one step, and make operations
that cross service boundaries safe to retry.

## Test the failure path

The failure path is the one most likely to go untested, which is exactly backwards. Cover it on purpose: give pseudos
canned failures (timeouts, 503s, malformed responses) alongside canned successes, and assert that the system fails the
way you designed it to.

# Part V: Hermetic Testing

The rest of this guide builds toward this part. Seams are what let a pseudo stand in; typed values and validation
shrink what a test has to cover; error codes and loud failures tell you what to reproduce. The goal here is simple:
**tests that are substantive and self-contained.** Substantive means the test exercises real behaviour and could fail
for a real reason. Self-contained means it brings everything it needs, so its result depends on your code and nothing
else.

Containers (Testcontainers and the like) made real infrastructure practical inside a test, and AI has made the
remaining cost, writing and maintaining pseudos, much cheaper. There is less excuse than there used to be.

## The goal: every bug becomes one more test case

You won't think of every edge case. Nobody does. A well-tested codebase is measured by what happens when someone finds
the one you missed: **reproducing it should be as quick as adding that exact input to a list.** The fixtures, seed
data, and pseudos already exist; you add the case, watch it fail, fix the code, and the case stays as a regression
test.

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

An outside-world case ("the payment provider returned a 200 with an empty body") is the same: one more canned response
for a pseudo, not a new test harness.

If reproducing a reported bug takes a day of setup or a live environment, the test infrastructure isn't finished,
however good the coverage looks. Self-contained tests make a failure's exact conditions cheap to recreate; the error
codes and debugging-grade messages from *"Error Oriented Programming"* tell you which conditions to recreate.

## If you can't test it, fix it

If the only way to test some code is against something already live (a shared staging database, a colleague's
service, a third-party sandbox), the problem is the code, not the test. Its dependencies are welded in: a client
constructed inside a method, a static singleton, a connection string read from a global. That same coupling makes the
code hard to change, reuse, and reason about; the test is just where it hurts first. The fix is a seam (see *"Program
to an interface, not an implementation"*) and constructor injection (see *"Inject through constructors"*), not a test
that reaches out into the world.

## Definitions

Two supporting concepts, less trivial than they sound.

### Internal/External Services

Think of a meteor. If one takes out your datacenter, an internal service is one the same meteor probably takes down
too. We won't define it more strictly than that.

The two are handled differently:

- **Internal services** (your own other services, your own database) run for real, in a container the test starts,
  where that's practical. Where it isn't (too heavy, or dragging half the fleet in behind it), give it a pseudo.
- **External services** (payment providers, third-party APIs, anything you don't own) get a pseudo.

### Pseudo-implementations

A *pseudo-implementation* (or *pseudo*) stands in for something the test can't run for real. It is a hand-written
class that implements the same interface, behaves plausibly, honours the contract, and fails loudly when the contract
is violated. Its data may be made up, but it is shaped like the real thing:

```java
public SomeRecord getData() {
  return new SomeRecord(List.of(1, 2, 3), "John Smith");
}
```

A stand-in that only satisfies the compiler (returning nulls, empty collections, or whatever makes the code run) is
not a pseudo. It tests nothing but the type system.

## What self-contained looks like

These are properties to aim for: a description of a good test, not a checklist to fail tests against. Missing one for
a good reason is fine, as long as you know which one and why.

- **It brings what it needs.** The databases, containers, and servers it talks to are started by the test or its
  fixture, seeded from known data, and cleaned up afterwards. (A container the test started is a fixture, not a
  running service.) Sharing a container across a suite is fine, and usually necessary for speed, as long as each test
  gets its own isolated state inside it.
- **It gives the same answer every time.** Inject the clock, timezone, locale, and randomness rather than reading them
  from the environment. Injecting a clock must not change what it measures, though: elapsed time (cache ages, timeouts)
  needs a monotonic source in production, because a wall clock jumps when someone changes it. Put `System.nanoTime()`
  behind the interface rather than swapping it for `InstantSource.system()`. Real infrastructure has its own
  non-determinism (the database's `now()`, sequence values, row order without an `ORDER BY`), so assert on what you
  control, not on generated IDs or unordered results.
- **It doesn't depend on its neighbours.** It passes alone, in any order, and in parallel with other tests, sharing no
  mutable state with them. With a real database this isn't free. Rolling back a transaction only works when the test
  and the code under test share a connection; tests that drive the app over the network run on the server's own
  connections. For those, give each test its own database or schema. In Postgres,
  `CREATE DATABASE test_n TEMPLATE seeded_db` makes that cheap.
- **It waits for events, not durations.** A `Thread.sleep` in a test is a race condition with a grace period. Wait on
  a signal instead: a latch, a readiness check, or a polled condition. A timeout on that wait is fine; its job is to
  fail the test, not to synchronise it.

A few smoke tests against real environments are fine, as long as they are labelled as such and kept out of the suite
that gates a merge. They have a real job: checking your pseudos against the services they imitate. A pseudo is only as
good as your understanding of the real thing, and smoke tests show you when that understanding has drifted.

For example, a fully hermetic test of a database-backed web app has:

- a realistic seed schema and data (one or several, as the tests require)
- a Postgres container initialized from that seed
- real reads and writes to that container, triggered by deterministic network requests against the fully functioning
  app

## Pseudo-implementations must be inspectable

Pseudos are the meat and potatoes of hermetic testing. `HttpGateway` touches the outside world, which makes it a seam
(see *"Program to an interface, not an implementation"*), and its interface is what lets a pseudo stand in:

```java
public interface HttpGateway {
  Response fetch(Request request);
}

public final class PseudoHttpGateway implements HttpGateway {

  private final List<Request> received = new ArrayList<>();
  private final Map<URI, Response> canned;

  public PseudoHttpGateway(Map<URI, Response> canned) {
    this.canned = Map.copyOf(canned);
  }

  @Override
  public Response fetch(Request request) {
    received.add(request);
    return Optional.ofNullable(canned.get(request.uri()))
        .orElseThrow(() -> new IllegalStateException("no canned response for " + request.uri()));
  }

  /** What the code under test actually asked for, in order. */
  public List<Request> received() {
    return List.copyOf(received);
  }
}
```

That `orElseThrow` matters as much as the recording. A pseudo that returns a default for an unexpected call hides the
bug where you fetched the wrong URL; one that fails loudly turns it into a test failure with a useful message.
**Pseudos are strict.**

The inspection surface is for asserting on *what happened*, not *how*. A request that leaves your system is an outcome
in its own right: the payment provider really was asked to charge that card, whatever route your code took to get
there. So `received()` belongs with the resulting state. Internal choreography doesn't: which of your own methods were
called, in what order, how many times. Assertions on outcomes survive a refactor; assertions on internal call
sequences break when the calls are restructured, even if the behaviour didn't change. Assert on the order of
`received()` only when the order is itself part of the contract (authorise before capture, say).
