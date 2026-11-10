package black.javastandards;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationException;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkerExecutor;

/**
 * Formats the Java sources with the profile ({@code format}), or only checks that they already are ({@code checkFormat}).
 * JDT runs in a worker with its own class loader, so it never mixes with the build's classes.
 */
@DisableCachingByDefault(because = "checkFormat has no outputs, and format changes its inputs in place")
public abstract class FormatTask extends DefaultTask {
	@InputFiles
	@SkipWhenEmpty
	@IgnoreEmptyDirectories
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSources();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getProfile();

	/** The Java version the sources are parsed as, for example "25". */
	@Input
	public abstract Property<String> getSourceLevel();

	@Classpath
	public abstract ConfigurableFileCollection getFormatterClasspath();

	/** True: only report files that aren't formatted. False: format them in place. */
	@Input
	public abstract Property<Boolean> getCheckOnly();

	/** Where file names in messages are relative to. */
	@Internal
	public abstract DirectoryProperty getBaseDir();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void run() throws IOException {
		File report = new File(getTemporaryDir(), "report.txt");
		Files.deleteIfExists(report.toPath());
		var queue = getWorkerExecutor().classLoaderIsolation(spec -> spec.getClasspath().from(getFormatterClasspath()));
		queue.submit(FormatAction.class, p -> {
			p.getFiles().from(getSources());
			p.getProfile().set(getProfile());
			p.getSourceLevel().set(getSourceLevel());
			p.getCheckOnly().set(getCheckOnly());
			p.getReport().set(report);
		});
		queue.await();

		List<String> changed = new ArrayList<>(), unparsable = new ArrayList<>();
		var base = getBaseDir().get().getAsFile().toPath();
		for (String line : Files.readAllLines(report.toPath())) {
			String[] kindAndPath = line.split("\t", 2);
			String name = base.relativize(new File(kindAndPath[1]).toPath()).toString();
			(kindAndPath[0].equals(FormatAction.UNPARSABLE) ? unparsable : changed).add(name);
		}
		unparsable.forEach(f -> getLogger().error("can't format (doesn't parse): {}", f));
		if (getCheckOnly().get()) {
			changed.forEach(f -> getLogger().error("not formatted: {}", f));
			if (!changed.isEmpty() || !unparsable.isEmpty()) {
				throw new VerificationException(changed.size() + " file(s) not formatted with the profile, " + unparsable.size()
					+ " that don't parse (listed above). `gradle format` formats them.");
			}
		} else {
			changed.forEach(f -> getLogger().lifecycle("formatted: {}", f));
			if (!unparsable.isEmpty()) { throw new GradleException(unparsable.size() + " file(s) don't parse, so weren't formatted (listed above)"); }
		}
	}
}
