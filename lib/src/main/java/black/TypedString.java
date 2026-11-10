package black;

/**
 * A tagging interface for a type that exists to hold and validate one shaped string: a SKU, a MAC address, a host name.
 * Holding one is proof it has that shape, so nothing downstream has to check again, and two of them can't be swapped by
 * accident. It declares nothing, because its user is the reader: "find implementations" on this is the catalogue of every
 * shaped value in a codebase.
 *
 * <p>Write it as a record whose compact constructor refuses anything else:
 *
 * <pre>{@code
 * record Sku(String value) implements TypedString {
 *   private static final Pattern SHAPE = Pattern.compile("[A-Z]{3}-\\d{6}");
 *
 *   public Sku {
 *     if (!SHAPE.matcher(value).matches()) {
 *       throw new IllegalArgumentException("not a SKU: " + value);
 *     }
 *   }
 *
 *   @Override
 *   public String toString() {
 *     return value;
 *   }
 * }
 * }</pre>
 *
 * <p>Implementations are prime candidates for value classes (JEP 401, still in preview) once those are ready: a small,
 * immutable wrapper is exactly what they're for, and the JVM can then skip the allocation or lay it out flat. Adopting one
 * should take a single word, {@code value record Sku(...)}, so keep implementations ready for it: a record or final class
 * with final fields, never used in {@code synchronized}, and never compared with {@code ==}.
 *
 * <p>For an opaque secret, such as a token, implement this too but give {@code toString()} a redacted form.
 *
 * @see <a href="https://github.com/Brendan-Black/black-java/blob/master/BLACK_JAVA.md#typed-strings-wrap-them">Typed strings: wrap them</a>
 * @see <a href="https://github.com/Brendan-Black/black-java/blob/master/BLACK_JAVA.md#value-classes">Value classes</a>
 * @see <a href="https://openjdk.org/jeps/401">JEP 401</a>
 */
public interface TypedString {}
