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
 * <p>For an opaque secret, such as a token, implement this too but give {@code toString()} a redacted form.
 *
 * @see <a href="https://github.com/Brendan-Black/black-java/blob/master/BLACK_JAVA.md#typed-strings-wrap-them">Typed strings: wrap them</a>
 */
public interface TypedString {}
