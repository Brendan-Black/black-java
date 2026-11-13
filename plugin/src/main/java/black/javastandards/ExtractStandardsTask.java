package black.javastandards;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes the formatter profile, the warning settings and the checks jar bundled with the plugin to files, for the other
 * tasks and the compiler to read.
 */
@DisableCachingByDefault(because = "it only writes three small files")
public abstract class ExtractStandardsTask extends DefaultTask {
	static final String PROFILE = "java-formatter.xml";
	static final String WARNINGS = "java-warnings.prefs";
	/** The checks module's annotation processors (see plugin/build.gradle.kts). */
	static final String CHECKS = "black-checks.jar";

	@Input
	public abstract Property<String> getProfileText();

	@Input
	public abstract Property<String> getWarningsText();

	/** The checks jar's SHA-256, so a new plugin version writes the jar again. */
	@Input
	public abstract Property<String> getChecksDigest();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	@TaskAction
	public void extract() throws IOException {
		var dir = getOutputDir().get().getAsFile().toPath();
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(PROFILE), getProfileText().get());
		Files.writeString(dir.resolve(WARNINGS), getWarningsText().get());
		Files.write(dir.resolve(CHECKS), bundledBytes(CHECKS));
	}

	/** The text of a file bundled in the plugin's jar. */
	static String bundled(String name) {
		return new String(bundledBytes(name), StandardCharsets.UTF_8);
	}

	/** The SHA-256 of a file bundled in the plugin's jar, in hex. */
	static String bundledDigest(String name) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundledBytes(name)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // every JDK has SHA-256
		}
	}

	private static byte[] bundledBytes(String name) {
		try (InputStream in = ExtractStandardsTask.class.getResourceAsStream(name)) {
			if (in == null) { throw new IllegalStateException("the plugin jar has no " + name); }
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
