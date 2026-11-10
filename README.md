# black-java

My Java standards: a style guide, [`BLACK_JAVA.md`](BLACK_JAVA.md), and the tooling below that enforces layout and compiler warnings.

## Formatter

My Java formatting rules, as an Eclipse JDT formatter profile: [`java-formatter.xml`](java-formatter.xml).
JDT is the formatter VS Code's Java extension (Red Hat) uses, and Eclipse and IntelliJ can import the same file.

Also a Gradle plugin that makes a build enforce them, and fail on any compiler warning the editor shows: see
[Gradle plugin](#gradle-plugin).

## The rules

- **Tabs.** One tab per indentation level, and one more for a wrapped line, not two.
- **`case` is indented inside `switch`.**
- **Empty bodies stay on one line:** `private Foo() {}`, `record Point(int x, int y) {}`.
- **Line breaks you put in stay.** The formatter never joins wrapped lines, never joins comment lines, keeps
  blank lines (up to 99 in a row), and leaves parentheses where you put them. So how a long call is laid out,
  for example `(` ending the line, one argument per line and `)` on its own line, is your choice, made per call.
- **Braces on every `if`/`else`/`for`/`while`/`do` body.** A short block may stay on one line as you wrote it:
  `if (x == null) { return; }`, `for (...) { total += n; }`, and the same for a lambda's block or a `switch`
  case's. JDT can't add a missing brace, but it never leaves a braceless body on its `if`'s line:
  `if (x == null) return;` gets split in two, so the missing braces stand out.
- **A labelled statement starts on the line after its label.**
- **Lines up to 160 wide**, comments included. HTML in Javadoc is left as written.
- **Array initializers as `{1, 2}`.**

Everything else is Eclipse's built-in default. The file lists only the settings that differ from it, plus the
three tab settings, which match the default but are spelled out because tabs are the point.

## Using it

### VS Code

Put this in `.vscode/settings.json` for one project, or in your user settings for every project:

```json
{
	"java.format.settings.url": "https://raw.githubusercontent.com/Brendan-Black/black-java/master/java-formatter.xml",
	"java.format.settings.profile": "Brendan",
	"[java]": {
		"editor.defaultFormatter": "redhat.java",
		"editor.insertSpaces": false
	}
}
```

`java.format.settings.url` also takes a file path, absolute or relative to the workspace folder, if you'd
rather keep a copy in the project. After changing it, run **Developer: Reload Window**.

`editor.insertSpaces` matters: the Java extension takes tabs-or-spaces from the editor, not from the profile.

### Eclipse

Preferences → Java → Code Style → Formatter → Import…, and pick `java-formatter.xml`.

### IntelliJ IDEA

Settings → Editor → Code Style → Java → ⚙ → Import Scheme → Eclipse XML Profile. IntelliJ only maps some
Eclipse settings onto its own, so check the result. For exact results, the "Adapter for Eclipse Code
Formatter" plugin runs JDT itself with this file.

## What JDT can't do

Checked against JDT 3.48 (VS Code Java extension 1.58):

- **A space inside empty braces.** JDT writes `{}`. The only setting for `{ }` is for array initializers.
- **Adding braces.** A formatter only moves code around; putting in a missing `{ }` is a code change. JDT does
  it outside the formatter, as an Eclipse clean-up: "Use blocks in if/while/for/do statements".
- **A one-line block in an `if` that has an `else`.** `if (a) { x(); } else { y(); }` and `} else { y(); }` are
  always spread over three lines each: the one-line setting covers only an `if` without an `else`.
  `} else if (b) { y(); }` stays, because that block belongs to the inner `if`.
- **Keeping every line break.** Breaks are kept wherever JDT is allowed to wrap. Some spots have no wrap
  point, and a break there gets joined:
  - right after a lambda's `->` (`s ->⏎ s.strip()`), and before `->`
  - after `throws`
  - on either side of a for-each's `:` (`for (String s :⏎ list)`)
  - right after `?` or `:` in a conditional (`b ?⏎ x :⏎ y`). A break *before* them is kept, and once there is
    one, JDT puts the other operator on its own line too. `wrap_before_conditional_operator=false` swaps that
    round: then breaks after them are kept and breaks before are joined. No setting keeps both.
  - inside the parentheses of a cast (`(⏎String) o`), or right inside a parenthesized expression (`(⏎a + b)`,
    `(a + b⏎)`). A break at an operator inside one, like `(a⏎+ b)`, is kept.
  - between a parameter's annotation and its type (`@Nullable⏎String s`). After the annotations on a field,
    method or class, a line break is always put in anyway.
  - between a name and its `.` or `(` (`System⏎.out`, `foo⏎(x)`)
  - a `{` on its own line (always moved up), and an empty body split over two lines (`{⏎}` becomes `{}`).
- **A blank line only before documented fields.** `blank_lines_before_field` applies to every field, so it's
  left at 0 and those blank lines are added by hand.
- **Lining things up inside `//` comments.** Line comments are formatted, so spaces that line a continued comment
  line up under the one above are taken out: `//⏎//         const char *out` becomes `// const char *out`. Keep such
  a comment on one line.

## Gotchas

- **Some keys are misspelled on purpose.** `parentheses_positions_in_for_statment` and
  `parentheses_positions_in_method_delcaration` are how JDT spells them (so is `keep_imple_if_on_one_line`, if you
  add it). Spelled correctly, they are ignored.
- **Not every key takes every value.** Given an unsupported value, some keys make JDT throw (the tools below
  stop with an error) and others are silently ignored. VS Code's XML extension flags some of the silent ones as
  you edit the file.
- **VS Code adds `// ` when you press Enter after `https?://`.** That's not the formatter: the Java extension
  continues `//` comments on Enter. Its rule skips `http://` but not `?://`, so it fires inside regexes like
  `"^https?://example\\.com"`. Ctrl+Z straight after the Enter takes the `// ` back out.

## Gradle plugin

`black.java-standards` makes a project's build enforce both files: `gradle check` fails on code that isn't formatted
with the profile, and on compiler warnings. The plugin carries its own copies of the two files, so a project gets a
fixed version of the rules, which only changes when it moves to a newer plugin version.

### Adding it

JitPack builds each tag of this repo. In the project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    // black.java-standards (formatting and compiler warnings) comes from JitPack, which serves it by its repository's name.
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "black.java-standards") useModule("com.github.Brendan-Black:black-java:${requested.version}")
    }
    // With https://github.com/Brendan-Black/black-java checked out next to this project, that checkout is used instead.
    if (file("../black-java/settings.gradle.kts").exists()) includeBuild("../black-java")
}
```

and in `build.gradle.kts`, with `mavenCentral()` among the project's repositories (the plugin downloads JDT and ECJ
from there):

```kotlin
plugins {
    java // or application, java-library
    id("black.java-standards") version "1.0.0"
}
```

The `includeBuild` line is for working on the rules: with this repo checked out next to the project, the project uses
the checkout, whatever version it names. CI and JitPack don't have the checkout, so they use the version.

### Tasks

| Task | What it does |
|---|---|
| `checkFormat` | Fails if a Java file isn't formatted with the profile, and lists the files. Run by `check`. |
| `format` | Formats every Java file with the profile, keeping each file's line endings. |
| `javaWarnings` | Compiles every source set with ECJ, writing nothing, and fails on compiler warnings. Run by `check`. Each source set also has its own task: `javaWarningsMain`, `javaWarningsTest`. |
| `javaStandardsIde` | Sets up VS Code to match: copies both files into `.vscode/`, and creates `.vscode/settings.json` pointing at them, or lists what to change in an existing one. |

The profile never takes away a line break, so `checkFormat` doesn't impose a layout: it only checks that a file is
what VS Code's Format Document would make of it. The build formats with `org.eclipse.jdt.core` 3.47.0 from Maven
Central, while VS Code's Java extension 1.58 has 3.48. On this repo's code and two projects' (88 files, indentation
stripped so there was work to do), both gave the same result.

Annotation processors run in `javaWarnings` as in the build. Lombok is loaded as ECJ needs it, as a Java agent.
Problems in generated code aren't reported.

### Settings

Everything has a default:

```kotlin
javaStandards {
    checkFormat = false            // check doesn't fail on unformatted code (default true)
    checkWarnings = false          // check doesn't fail on compiler warnings (default true)
    failOn = "info"                // the least severe compiler problem that fails: "error", "warning" (default) or "info"
    honorSuppressWarnings = false  // also count what @SuppressWarnings hides (default true)
    formatterProfile = file("my-formatter.xml")  // instead of the plugin's java-formatter.xml
    warningSettings = file("my-warnings.prefs")  // instead of the plugin's java-warnings.prefs
    jdtVersion = "3.47.0"          // org.eclipse.jdt:org.eclipse.jdt.core, which formats
    ecjVersion = "3.46.100"        // org.eclipse.jdt:ecj, which checks warnings
}
```

`honorSuppressWarnings = false` makes the build stricter than the editor, which still hides what `@SuppressWarnings`
hides.

## Warnings

[`java-warnings.prefs`](java-warnings.prefs) says which compiler checks are on, and how severe each is. The editor and
the [Gradle plugin](#gradle-plugin) both read it and run the same compiler (Eclipse's, ECJ), so a build fails exactly
when the editor shows a warning. The file lists every check, set as Eclipse's defaults, which are what VS Code shows
out of the box.

Without the plugin, point VS Code at it in `.vscode/settings.json` or your user settings:

```json
"java.settings.url": "https://raw.githubusercontent.com/Brendan-Black/black-java/master/java-warnings.prefs"
```

### Tightness

Each check's value in the prefs file is the main dial:

| Value | Editor | Build |
|---|---|---|
| `error`, `warning` | shown | fails |
| `info` | shown, in blue | passes (unless `failOn = "info"`) |
| `ignore` | not checked | not checked |

To change it for one project, copy the prefs file into the project, set `warningSettings` to the copy, and run
`javaStandardsIde` so VS Code uses it too. Some checks have `enabled`/`disabled` settings right after them that
fine-tune them, for example whether `unusedParameter` counts parameters of overriding methods. Null analysis only runs
with `annotation.nullanalysis=enabled`.

### What it doesn't check

Only the compiler's warnings: the ones in the editor's Problems panel with source "Java". Not build-file warnings
(Gradle), spelling, or other extensions' (SonarLint and so on). `TODO` comments aren't warnings in VS Code and aren't
reported.

## Tools

Two programs that run JDT from the command line, using the jars VS Code's Java extension already ships.
`JARS` is its `server/plugins` folder plus `*` (quote it). Needs Java 25, which the extension also ships, in
its `jre` folder:

- Windows: `%USERPROFILE%\.vscode\extensions\redhat.java-1.58.0-win32-x64\server\plugins\*`
- WSL or Linux with VS Code Remote: `~/.vscode-server/extensions/redhat.java-1.58.0-linux-x64/server/plugins/*`,
  and Java is `~/.vscode-server/extensions/redhat.java-1.58.0-linux-x64/jre/*/bin/java`. From WSL, a Windows JDK
  only runs as `java.exe`, and it needs Windows paths for `-cp`, so use the Linux one.

`Check.java` uses `Fmt.java`'s code, so keep the two side by side (Java finds `Fmt.java` next to the file it
runs).

- **`tools/Fmt.java`** formats files in place, the way VS Code would, keeping each file's line endings. A file
  that's already formatted isn't touched. A file that doesn't parse is reported and skipped, and the exit code
  is then 1:

  ```sh
  java -cp "JARS" tools/Fmt.java java-formatter.xml $(git ls-files '*.java')
  ```

- **`tools/Check.java`** checks that a profile never takes away a line break. Each file is formatted as it
  is, and again with extra line breaks put in at random where people break lines. It reports every line break
  or blank line that was lost, and any file that changes when formatted a second time. It writes nothing.

  ```sh
  java -cp "JARS" tools/Check.java java-formatter.xml 1 $(git ls-files '*.java')
  ```

  The `1` is the random seed. Files as written should come through with nothing lost. The extra-breaks pass
  only ever loses the kinds listed under [What JDT can't do](#what-jdt-cant-do).

## License

[MIT](LICENSE)
