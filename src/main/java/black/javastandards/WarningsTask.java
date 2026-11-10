package black.javastandards;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationException;
import org.gradle.work.DisableCachingByDefault;

/**
 * Compiles one source set with ECJ, the compiler VS Code's Java extension uses, with the warning settings, writing no class
 * files, and fails if it reports a problem at {@code failOn} or worse.
 */
@DisableCachingByDefault(because = "it only checks: there are no outputs")
public abstract class WarningsTask extends JavaExec {
	private static final String SUPPRESS_WARNINGS = "org.eclipse.jdt.core.compiler.problem.suppressWarnings";

	@InputFiles
	@SkipWhenEmpty
	@IgnoreEmptyDirectories
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSources();

	/** The source folders, which ECJ compiles whole. */
	@Internal
	public abstract ConfigurableFileCollection getSourceDirs();

	@Classpath
	public abstract ConfigurableFileCollection getCompileClasspath();

	@Classpath
	public abstract ConfigurableFileCollection getProcessorPath();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getSettings();

	@Input
	public abstract Property<String> getFailOn();

	@Input
	public abstract Property<Boolean> getHonorSuppressWarnings();

	@Input
	public abstract Property<Integer> getRelease();

	@Override
	@TaskAction
	public void exec() {
		String level = getFailOn().get();
		if (!List.of("error", "warning", "info").contains(level)) {
			throw new IllegalArgumentException("javaStandards.failOn is '" + level + "'; it can be 'error', 'warning' or 'info'");
		}
		File dir = getTemporaryDir();
		File effective = new File(dir, "effective.prefs");
		writeEffectiveSettings(getSettings().get().getAsFile(), effective, level);

		List<String> a = new ArrayList<>(List.of("--release", getRelease().get().toString(), "-encoding", "UTF-8", "-properties",
			effective.getPath(), "-d", "none"));
		// Leave out entries that don't exist (yet), like an empty resources folder: ECJ warns about each.
		String cp = getCompileClasspath().getFiles().stream().filter(File::exists).map(File::getPath).collect(Collectors.joining(File.pathSeparator));
		if (!cp.isEmpty()) { a.addAll(List.of("-cp", cp)); }
		if (getProcessorPath().isEmpty()) {
			a.add("-proc:none");
		} else {
			File generated = new File(dir, "generated");
			generated.mkdirs();
			a.addAll(List.of("-processorpath", getProcessorPath().getAsPath(), "-s", generated.getPath(), "-nowarn:[" + generated.getPath() + "]"));
			// Lombok changes ECJ's own code, so it only works loaded as an agent into the JVM running ECJ.
			// (The second flag stops Java 24+ warning that Lombok uses sun.misc.Unsafe.)
			getProcessorPath().getFiles().stream().filter(f -> f.getName().matches("lombok-[0-9].*\\.jar")).findFirst()
				.ifPresent(lombok -> jvmArgs("-javaagent:" + lombok.getPath() + "=ECJ", "--sun-misc-unsafe-memory-access=allow"));
		}
		if (!level.equals("error")) { a.add("-failOnWarning"); }
		getSourceDirs().getFiles().stream().filter(File::isDirectory).map(File::getPath).forEach(a::add);
		setArgs(a);
		setIgnoreExitValue(true);
		super.exec();
		if (getExecutionResult().get().getExitValue() != 0) {
			throw new VerificationException("Java compiler problems at '" + level + "' or worse in " + getName() + ", listed above");
		}
	}

	/** The settings with failOn and honorSuppressWarnings applied: with failOn 'info', every info becomes a warning. */
	private void writeEffectiveSettings(File settings, File effective, String level) {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(settings.toPath())) {
			p.load(r);
		} catch (IOException e) {
			throw new IllegalArgumentException("can't read the warning settings " + settings + ": " + e.getMessage(), e);
		}
		if (level.equals("info")) {
			for (String key : p.stringPropertyNames()) {
				if (p.getProperty(key).equals("info")) { p.setProperty(key, "warning"); }
			}
		}
		if (!getHonorSuppressWarnings().get()) { p.setProperty(SUPPRESS_WARNINGS, "disabled"); }
		try (Writer w = Files.newBufferedWriter(effective.toPath())) {
			p.store(w, "javaStandards: " + settings + ", failOn " + level);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
