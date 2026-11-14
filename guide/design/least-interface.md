# Java: declare the least interface

Declare `Map`, not `HashMap`, unless you depend on something only the concrete type gives you, such as the iteration
order of a `LinkedHashMap`.

```java
interface HasId { Object id(); }
interface CacheKey<T> extends HasId { }

void invalidateUser(CacheKey<User> key) {    // right: an operation on user keys
  userCache.evict(key.id());
}

void invalidateUser(HasId key) { ... }        // compiles, loses the identity
void invalidateUser(CacheKey<?> key) { ... }  // more frustratingly, the same
```
