package black.javastandards;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * The black.java-standards plugin. With the java plugin, it adds:
 * <ul>
 * <li>{@code checkFormat}, run by {@code check}: fails if a Java file isn't formatted with the profile;
 * <li>{@code format}: formats every Java file with the profile;
 * <li>{@code javaWarnings} (and one task per source set, {@code javaWarningsMain}...), run by {@code check}: fails on
 * compiler warnings;
 * <li>the checks (BLACK_JAVA.md's hard rules, such as no {@code Optional} in a constructor) on every source set's
 * annotation processor path, so breaking one is a compiler error in {@code compileJava} and {@code javaWarnings} alike;
 * <li>{@code javaStandardsIde}: sets up VS Code with the same profile and warning settings.
 * </ul>
 * Settings: {@link JavaStandardsExtension}.
 */
public class JavaStandardsPlugin implements Plugin<Project> {
	/** The formatter, from Maven Central. VS Code's Java extension 1.58 ships 3.48, which isn't on Maven Central yet. */
	static final String JDT_VERSION = "3.47.0";
	static final String ECJ_VERSION = "3.46.100";
	static final String GROUP = "verification";

	@Override
	public void apply(Project project) {
		var ext = project.getExtensions().create("javaStandards", JavaStandardsExtension.class);
		ext.getCheckFormat().convention(true);
		ext.getCheckWarnings().convention(true);
		ext.getFailOn().convention("warning");
		ext.getHonorSuppressWarnings().convention(true);
		ext.getJdtVersion().convention(JDT_VERSION);
		ext.getEcjVersion().convention(ECJ_VERSION);

		var extract = project.getTasks().register("javaStandardsFiles", ExtractStandardsTask.class, t -> {
			t.setDescription("Writes the bundled formatter profile and warning settings to build/java-standards.");
			t.getProfileText().set(ExtractStandardsTask.bundled(ExtractStandardsTask.PROFILE));
			t.getWarningsText().set(ExtractStandardsTask.bundled(ExtractStandardsTask.WARNINGS));
			t.getChecksDigest().set(ExtractStandardsTask.bundledDigest(ExtractStandardsTask.CHECKS));
			t.getOutputDir().set(project.getLayout().getBuildDirectory().dir("java-standards"));
		});
		ext.getFormatterProfile().convention(extract.flatMap(t -> t.getOutputDir().file(ExtractStandardsTask.PROFILE)));
		ext.getWarningSettings().convention(extract.flatMap(t -> t.getOutputDir().file(ExtractStandardsTask.WARNINGS)));

		project.getTasks().register("javaStandardsIde", IdeTask.class, t -> {
			t.setGroup("ide");
			t.setDescription("Sets up VS Code (.vscode/) to format and warn the way the build checks.");
			t.getProfile().set(ext.getFormatterProfile());
			t.getWarningSettings().set(ext.getWarningSettings());
			t.getProjectDir().set(project.getLayout().getProjectDirectory());
		});

		project.getPluginManager().withPlugin("java", p -> configureJava(project, ext, extract));
	}

	private static void configureJava(Project project, JavaStandardsExtension ext, TaskProvider<ExtractStandardsTask> extract) {
		var java = project.getExtensions().getByType(JavaPluginExtension.class);
		var tasks = project.getTasks();
		Provider<Integer> defaultRelease = project.provider(() -> Integer.valueOf(java.getSourceCompatibility().getMajorVersion()));

		Configuration jdt = project.getConfigurations().create("javaStandardsFormatter", c -> {
			c.setCanBeConsumed(false);
			c.setDescription("JDT, which the format tasks run");
		});
		project.getDependencies().addProvider(jdt.getName(), ext.getJdtVersion().map(v -> "org.eclipse.jdt:org.eclipse.jdt.core:" + v));
		Configuration ecj = project.getConfigurations().create("javaStandardsEcj", c -> {
			c.setCanBeConsumed(false);
			c.setDescription("ECJ, which the javaWarnings tasks run");
		});
		project.getDependencies().addProvider(ecj.getName(), ext.getEcjVersion().map(v -> "org.eclipse.jdt:ecj:" + v));

		// The main source set's Java version: the formatter parses every source set as it.
		Provider<Integer> mainRelease = tasks.named("compileJava", JavaCompile.class).flatMap(t -> t.getOptions().getRelease()).orElse(defaultRelease);

		TaskProvider<FormatTask> checkFormat = tasks.register("checkFormat", FormatTask.class, t -> {
			t.setGroup(GROUP);
			t.setDescription("Fails if a Java file isn't formatted with the profile (javaStandards.checkFormat).");
			t.getCheckOnly().set(true);
			t.onlyIf("javaStandards.checkFormat is true", x -> ext.getCheckFormat().get());
		});
		TaskProvider<FormatTask> format = tasks.register("format", FormatTask.class, t -> {
			t.setGroup("formatting");
			t.setDescription("Formats every Java file with the profile.");
			t.getCheckOnly().set(false);
		});
		for (var t : List.of(checkFormat, format)) {
			t.configure(task -> {
				task.getProfile().set(ext.getFormatterProfile());
				task.getSourceLevel().set(mainRelease.map(String::valueOf));
				task.getFormatterClasspath().from(jdt);
				task.getBaseDir().set(project.getLayout().getProjectDirectory());
			});
		}

		var warnings = tasks.register("javaWarnings", t -> {
			t.setGroup(GROUP);
			t.setDescription("Fails on compiler warnings in any source set (javaStandards.checkWarnings).");
		});
		tasks.named("check", t -> t.dependsOn(checkFormat, warnings));

		var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
		// The hard rules are compiler errors, from annotation processors: on every source set's processor path, javac
		// (compileJava...) and ECJ (javaWarnings...) both report them. Not optional, so there is no setting.
		var checks = project.files(extract.flatMap(t -> t.getOutputDir().file(ExtractStandardsTask.CHECKS))).builtBy(extract);
		java.getSourceSets().configureEach(ss -> {
			project.getDependencies().add(ss.getAnnotationProcessorConfigurationName(), checks);
			checkFormat.configure(t -> t.getSources().from(ss.getJava()));
			format.configure(t -> t.getSources().from(ss.getJava()));
			var w = tasks.register("javaWarnings" + capitalize(ss.getName()), WarningsTask.class,
				t -> configureWarnings(t, ss, ext, ecj, defaultRelease, tasks.named(ss.getCompileJavaTaskName(), JavaCompile.class), toolchains, java));
			warnings.configure(t -> t.dependsOn(w));
		});
	}

	private static void configureWarnings(WarningsTask t, SourceSet ss, JavaStandardsExtension ext, Configuration ecj, Provider<Integer> defaultRelease,
		TaskProvider<JavaCompile> compile, JavaToolchainService toolchains, JavaPluginExtension java) {
		t.setGroup(GROUP);
		t.setDescription("Fails on compiler warnings in the " + ss.getName() + " source set.");
		t.setClasspath(ecj);
		t.getMainClass().set("org.eclipse.jdt.internal.compiler.batch.Main");
		t.getSources().from(ss.getJava());
		t.getSourceDirs().from(ss.getJava().getSrcDirs());
		t.getCompileClasspath().from(ss.getCompileClasspath());
		t.getProcessorPath().from(ss.getAnnotationProcessorPath());
		t.getSettings().set(ext.getWarningSettings());
		t.getFailOn().set(ext.getFailOn());
		t.getHonorSuppressWarnings().set(ext.getHonorSuppressWarnings());
		t.getRelease().set(compile.flatMap(c -> c.getOptions().getRelease()).orElse(defaultRelease));
		t.getJavaLauncher().convention(toolchains.launcherFor(java.getToolchain()));
		t.onlyIf("javaStandards.checkWarnings is true", x -> ext.getCheckWarnings().get());
	}

	/** main → Main, so the source set's task is javaWarningsMain. */
	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
