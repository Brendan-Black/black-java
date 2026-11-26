# Java: a jar per unit

"Package" here is the thing you ship, a jar, not a Java `package`. A unit that a second project wants gets its own
repository, and JitPack builds each tag of it into a jar:

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.Brendan-Black:TypedString:1.0.0") }
```

- **A repository per unit, a tag per release.** The tag is the version; `jitpack.yml` names the JDK that builds it.
  [TypedString](https://github.com/Brendan-Black/TypedString) is the pattern: one interface, no dependencies.
- **`api`, not `implementation`,** in a library whose public types use the unit, since its users compile against the
  unit too. This repo's `lib` passes TypedString on that way.
- **Depend on as little as you can.** Whatever the unit depends on, every project that references it now does.
- **Compile for the oldest Java its users run.** TypedString is built for Java 17.
