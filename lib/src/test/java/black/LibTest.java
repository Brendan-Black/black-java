package black;

/**
 * Checks lib still gives its users TypedString now that it lives in its own repository; exits non-zero on a failure.
 * The build's checkPackaging then checks lib passes it on to whatever compiles against lib.
 */
public final class LibTest {
	private static int failures;
	private static int checks;

	private LibTest() {}

	/** What a user of lib writes: it must compile with lib alone as the dependency. */
	record Sku(String value) implements TypedString {}

	public static void main(String[] args) {
		check(new Sku("ABC-123456") instanceof TypedString, "a record implementing TypedString compiles and runs against lib");

		// One copy of black.TypedString, from TypedString's jar: a second copy in lib's would make which one loads
		// depend on class path order.
		String typedStringJar = String.valueOf(TypedString.class.getProtectionDomain().getCodeSource().getLocation());
		String libClasses = String.valueOf(MagicStrings.class.getProtectionDomain().getCodeSource().getLocation());
		check(typedStringJar.contains("TypedString"), "TypedString comes from its own jar: " + typedStringJar);
		check(!typedStringJar.equals(libClasses), "TypedString doesn't come from lib: " + libClasses);
		check(TypedString.class.getClassLoader().getResource("black/TypedString.class") != null, "TypedString is on the class path");

		System.out.println(checks - failures + "/" + checks + " checks passed");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void check(boolean passed, String what) {
		checks++;
		if (!passed) {
			failures++;
			System.out.println("FAIL: " + what);
		}
	}
}
