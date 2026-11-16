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
 *     magicStringsElsewhere = "warning" // a value that is never an argument: "ignore" (default), "warning", "error"
 *     magicStringsWhitespace = true // also check values with whitespace in them (default false)
 *     magicStringsProximity = 3     // lines a value's literals can span and be read together: 0 (default); -1 file, -2 package, -3 module
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
	 * are all assignments, returns, {@code case} labels and the like): "ignore" (the default), "warning" or "error". Arguments
	 * are the pattern the check is for; the rest is reported only if a project asks.
	 */
	public abstract Property<String> getMagicStringsElsewhere();

	/**
	 * Whether a value with whitespace in it (a space, a tab, a newline) is checked. False by default: keys, headers and flags have
	 * none, so such a value is almost always text for people.
	 */
	public abstract Property<Boolean> getMagicStringsWhitespace();

	/**
	 * How close a value's literals must all be to be read together, and so not reported, however many are arguments: a
	 * number of lines (0, the default, is one line; 3 is within three lines of each other, in one file), -1 one file, -2 one
	 * package, or -3 the module (the whole source set), which reports nothing. A constant among them is never close.
	 */
	public abstract Property<Integer> getMagicStringsProximity();

	/** The Eclipse formatter profile. Default: the java-formatter.xml bundled with the plugin. */
	public abstract RegularFileProperty getFormatterProfile();

	/** The compiler warning settings (org.eclipse.jdt.core.prefs format). Default: the bundled java-warnings.prefs. */
	public abstract RegularFileProperty getWarningSettings();

	/** The version of org.eclipse.jdt:org.eclipse.jdt.core that formats. */
	public abstract Property<String> getJdtVersion();

	/** The version of org.eclipse.jdt:ecj that checks warnings. */
	public abstract Property<String> getEcjVersion();
}
