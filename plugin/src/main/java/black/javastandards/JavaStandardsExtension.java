package black.javastandards;

import org.gradle.api.file.RegularFileProperty;
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

	/** The Eclipse formatter profile. Default: the java-formatter.xml bundled with the plugin. */
	public abstract RegularFileProperty getFormatterProfile();

	/** The compiler warning settings (org.eclipse.jdt.core.prefs format). Default: the bundled java-warnings.prefs. */
	public abstract RegularFileProperty getWarningSettings();

	/** The version of org.eclipse.jdt:org.eclipse.jdt.core that formats. */
	public abstract Property<String> getJdtVersion();

	/** The version of org.eclipse.jdt:ecj that checks warnings. */
	public abstract Property<String> getEcjVersion();
}
