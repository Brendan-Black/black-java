package black.javastandards;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationException;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkerExecutor;

/**
 * {@code checkMagicStrings}: fails if a string value appears in more than one literal of the main source set, by the
 * project's {@code javaStandards.magicStrings} level and its per-package overrides. See {@link MagicStringsAction}.
 */
@DisableCachingByDefault(because = "it has no outputs")
public abstract class MagicStringsTask extends DefaultTask {
	@InputFiles
	@SkipWhenEmpty
	@IgnoreEmptyDirectories
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSources();

	/** The Java version the sources are parsed as, for example "25". */
	@Input
	public abstract Property<String> getSourceLevel();

	/** "error", "warning" or "ignore". */
	@Input
	public abstract Property<String> getLevel();

	/** Package name → level, for that package and the ones below it. */
	@Input
	public abstract MapProperty<String, String> getLevelByPackage();

	/** The most severe a value that is never an argument can be: "error", "warning" or "ignore". */
	@Input
	public abstract Property<String> getElsewhere();

	/** Whether values with whitespace in them are counted. */
	@Input
	public abstract Property<Boolean> getWhitespace();

	/** How close a value's literals must all be to be read together: "statement", "method", "file", "package" or "module". */
	@Input
	public abstract Property<String> getProximity();

	/** Generated sources live here, so files under it aren't read. */
	@Internal
	public abstract DirectoryProperty getBuildDir();

	@Classpath
	public abstract ConfigurableFileCollection getParserClasspath();

	/** Where file names in messages are relative to. */
	@Internal
	public abstract DirectoryProperty getBaseDir();

	/** Whether to list the magic strings that only warn, not just count them: {@code checkMagicStrings --warnings}. */
	private boolean listWarnings;

	@Option(option = "warnings", description = "Also lists the magic strings that only warn, not just how many there are.")
	public void setListWarnings(boolean listWarnings) {
		this.listWarnings = listWarnings;
	}

	@Internal
	public boolean getListWarnings() {
		return listWarnings;
	}

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() throws IOException {
		// Fail on a misspelt level before parsing anything.
		MagicStringsAction.Level.parse(getLevel().get());
		getLevelByPackage().get().values().forEach(MagicStringsAction.Level::parse);
		MagicStringsAction.Level.parse(getElsewhere().get());
		MagicStringsPolicy.Proximity.parse(getProximity().get());

		File report = new File(getTemporaryDir(), "report.txt");
		Files.deleteIfExists(report.toPath());
		var queue = getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(getParserClasspath()));
		queue.submit(MagicStringsAction.class, p -> {
			p.getFiles().from(getSources());
			p.getSourceLevel().set(getSourceLevel());
			p.getLevel().set(getLevel());
			p.getLevelByPackage().set(getLevelByPackage());
			p.getElsewhere().set(getElsewhere());
			p.getWhitespace().set(getWhitespace());
			p.getProximity().set(getProximity());
			p.getBuildDir().set(getBuildDir());
			p.getBaseDir().set(getBaseDir());
			p.getReport().set(report);
		});
		queue.await();

		int errors = 0;
		int unlisted = 0;
		boolean error = false;
		for (String line : Files.readAllLines(report.toPath())) {
			if (!line.startsWith("\t")) {
				String[] levelAndMessage = line.split("\t", 2);
				error = levelAndMessage[0].equals(MagicStringsAction.Level.ERROR.word);
				if (error) { errors++; } else if (!listWarnings) { unlisted++; }
				line = levelAndMessage[0] + ": " + levelAndMessage[1];
			}
			// Errors are always listed; warnings, which don't fail the build, only on request, so they aren't a wall of text
			// nobody reads on every run.
			if (!error && !listWarnings) { continue; }
			if (error) {
				getLogger().error(line);
			} else {
				getLogger().warn(line);
			}
		}
		if (unlisted > 0) {
			getLogger().warn("warning: " + unlisted + " more magic string(s) that only warn; checkMagicStrings --warnings lists them");
		}
		if (errors > 0) {
			throw new VerificationException(errors + " magic string(s), listed above: name each one, with a constant or an enum."
				+ " javaStandards.magicStrings, magicStringsByPackage and magicStringsElsewhere relax it.");
		}
	}
}
