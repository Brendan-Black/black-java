package black.javastandards;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Sets up VS Code to format and warn the way the build checks: copies the profile and the warning settings into .vscode/,
 * and points .vscode/settings.json at them. An existing settings.json is never edited: missing settings are listed instead.
 */
@UntrackedTask(because = "it writes into the project's .vscode folder")
public abstract class IdeTask extends DefaultTask {
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getProfile();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getWarningSettings();

	/** The project folder: VS Code's workspace folder. */
	@Internal
	public abstract DirectoryProperty getProjectDir();

	@TaskAction
	public void run() throws IOException {
		Path vscode = getProjectDir().get().getAsFile().toPath().resolve(".vscode");
		Files.createDirectories(vscode);
		Path profile = getProfile().get().getAsFile().toPath(), warnings = getWarningSettings().get().getAsFile().toPath();
		Files.copy(profile, vscode.resolve(ExtractStandardsTask.PROFILE), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(warnings, vscode.resolve(ExtractStandardsTask.WARNINGS), StandardCopyOption.REPLACE_EXISTING);
		getLogger().lifecycle("Wrote .vscode/{} and .vscode/{}", ExtractStandardsTask.PROFILE, ExtractStandardsTask.WARNINGS);

		Map<String, String> wanted = new LinkedHashMap<>();
		wanted.put("java.format.settings.url", "\".vscode/" + ExtractStandardsTask.PROFILE + "\"");
		wanted.put("java.format.settings.profile", "\"" + profileName(profile) + "\"");
		wanted.put("java.settings.url", "\".vscode/" + ExtractStandardsTask.WARNINGS + "\"");
		wanted.put("[java]", "{ \"editor.defaultFormatter\": \"redhat.java\", \"editor.insertSpaces\": false }");

		Path settings = vscode.resolve("settings.json");
		if (!Files.exists(settings)) {
			List<String> lines = new ArrayList<>();
			wanted.forEach((k, v) -> lines.add("\t\"" + k + "\": " + v));
			Files.writeString(settings, "{\n" + String.join(",\n", lines) + "\n}\n");
			getLogger().lifecycle("Wrote .vscode/settings.json. In VS Code, run Developer: Reload Window.");
			return;
		}
		String text = Files.readString(settings);
		List<String> missing = new ArrayList<>();
		wanted.forEach((k, v) -> {
			// A plain string setting has to have this value (the old copy of the profile won't do); [java] only has to exist.
			String pattern = Pattern.quote("\"" + k + "\"") + (v.startsWith("\"") ? "\\s*:\\s*" + Pattern.quote(v) : "");
			if (!Pattern.compile(pattern).matcher(text).find()) { missing.add("\t\"" + k + "\": " + v + ","); }
		});
		if (missing.isEmpty()) {
			getLogger().lifecycle(".vscode/settings.json already has the settings.");
		} else {
			getLogger().warn("Set these in .vscode/settings.json (add them, or change the ones that are there), then run Developer: Reload Window:\n{}",
				String.join("\n", missing));
		}
	}

	/** The profile's name, which java.format.settings.profile has to match. */
	private static String profileName(Path profile) throws IOException {
		Matcher m = Pattern.compile("<profile[^>]*\\bname=\"([^\"]+)\"").matcher(Files.readString(profile));
		return m.find() ? m.group(1) : "";
	}
}
