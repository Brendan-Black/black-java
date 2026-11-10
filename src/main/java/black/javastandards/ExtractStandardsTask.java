package black.javastandards;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Writes the formatter profile and warning settings bundled with the plugin to files, for the other tasks to read. */
@DisableCachingByDefault(because = "it only writes two small files")
public abstract class ExtractStandardsTask extends DefaultTask {
	static final String PROFILE = "java-formatter.xml";
	static final String WARNINGS = "java-warnings.prefs";

	@Input
	public abstract Property<String> getProfileText();

	@Input
	public abstract Property<String> getWarningsText();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	@TaskAction
	public void extract() throws IOException {
		var dir = getOutputDir().get().getAsFile().toPath();
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(PROFILE), getProfileText().get());
		Files.writeString(dir.resolve(WARNINGS), getWarningsText().get());
	}

	/** The text of a file bundled in the plugin's jar. */
	static String bundled(String name) {
		try (InputStream in = ExtractStandardsTask.class.getResourceAsStream(name)) {
			if (in == null) { throw new IllegalStateException("the plugin jar has no " + name); }
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
