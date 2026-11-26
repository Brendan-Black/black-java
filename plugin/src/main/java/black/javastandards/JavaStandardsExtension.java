package black.javastandards;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code javaStandards { }} block. Everything has a default, so a project only sets what it changes:
 *
 * <pre>
 * javaStandards {
 *     checkFormat = false          // don't fail check on unformatted code (default true)
 *     checkWarnings = false        // don't fail check on compiler warnings (default true)
 *     failOn = "info"              // the least severe compiler problem that fails: "error", "warning" (default), "info"
 *     honorSuppressWarnings = false // also count what @SuppressWarnings hides (default true)
 *     magicStrings = "warning"     // repeated string literals: "error" (default), "warning", "ignore"
 *     magicStringsByPackage = mapOf("com.example.legacy" to "ignore") // the same, for a package and those below it
 *     magicStringsElsewhere = "warning" // a value that is never an argument: "error" (default), "warning", "ignore"
 *     magicStringsWhitespace = true // also check values with whitespace in them (default false)
 *     magicStringsProximity = "file" // how close literals may sit and go unreported: "none" (default), "statement", "method", "file", "package", "module"
 *     formatterProfile = file("my-formatter.xml") // instead of the bundled java-formatter.xml
 *     warningSettings = file("my-warnings.prefs") // instead of the bundled java-warnings.prefs
 * }
 * </pre>
 */
public abstract class JavaStandardsExtension {
	/** Whether {@code check} fails when a Java file isn't formatted with the profile. */
	public abstract Property<Boolean> getCheckFormat();

	/** Whether {@code check} fails on compiler warnings. */
	public abstract Property<Boolean> getCheckWarnings();

	/** The least severe compiler problem that fails the build: "error", "warning" or "info". */
	public abstract Property<String> getFailOn();

	/** Whether problems hidden by {@code @SuppressWarnings} stay hidden. */
	public abstract Property<Boolean> getHonorSuppressWarnings();

	/**
	 * What {@code checkMagicStrings} does with a string value repeated in the main source set: "error" (the default) fails
	 * {@code check}, "warning" only reports, "ignore" doesn't look.
	 */
	public abstract Property<String> getMagicStrings();

	/**
	 * {@link #getMagicStrings()} for a package and the packages below it, keyed by package name:
	 * {@code magicStringsByPackage = mapOf("com.example.legacy" to "warning")}. The longest matching name wins. A value
	 * repeated across packages takes the most severe of their levels; a package set to "ignore" isn't counted at all.
	 */
	public abstract MapProperty<String, String> getMagicStringsByPackage();

	/**
	 * The most severe a repeated value can be when none of its literals is an argument to a method or constructor (when they
	 * are all assignments, returns, {@code case} labels and the like): "error" (the default), "warning" or "ignore". A value
	 * written twice is an error whether or not it is passed on.
	 */
	public abstract Property<String> getMagicStringsElsewhere();

	/**
	 * Whether a value with whitespace in it (a space, a tab, a newline) is checked. False by default: keys, headers and flags have
	 * none, so such a value is almost always text for people.
	 */
	public abstract Property<Boolean> getMagicStringsWhitespace();

	/**
	 * How close a value's literals may all sit and go unreported, however many are arguments: "none" (the default: every
	 * repeat is reported), "statement" (one statement, or a case's or if's head with its first statement), "method", "file",
	 * "package", or "module" (the whole source set), which reports nothing. A constant among them is never close. A
	 * package-info.java or module-info.java can set this and the other magic-string settings with {@code @black.MagicStrings}.
	 */
	public abstract Property<String> getMagicStringsProximity();

	/** The Eclipse formatter profile. Default: the java-formatter.xml bundled with the plugin. */
	public abstract RegularFileProperty getFormatterProfile();

	/** The compiler warning settings (org.eclipse.jdt.core.prefs format). Default: the bundled java-warnings.prefs. */
	public abstract RegularFileProperty getWarningSettings();

	/** The version of org.eclipse.jdt:org.eclipse.jdt.core that formats. */
	public abstract Property<String> getJdtVersion();

	/** The version of org.eclipse.jdt:ecj that checks warnings. */
	public abstract Property<String> getEcjVersion();
}
