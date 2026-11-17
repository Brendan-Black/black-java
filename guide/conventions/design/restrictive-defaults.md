# Java: final, immutable, package-private

- **`final` classes.** The "worker class" behind a seam is concrete and final.
- **Immutable.** Fields `final`, collections copied on the way in (`List.copyOf`) and never handed out mutable.
  Records do most of this for you.
- **Package-private** by default; `public` only for what other packages need. Package-private is how `trusted` is
  fenced off ([trusted-path](../values/trusted-path.md)).
