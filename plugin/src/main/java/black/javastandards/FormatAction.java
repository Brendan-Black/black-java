package black.javastandards;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Formats each file the way VS Code's Java extension would (the same JDT formatter and profile), keeping its line endings.
 * Writes one line per file that isn't formatted ({@link #CHANGED}) or doesn't parse ({@link #UNPARSABLE}) to the report.
 * Runs in a worker whose class loader has JDT.
 */
public abstract class FormatAction implements WorkAction<FormatAction.Parameters> {
	static final String CHANGED = "changed", UNPARSABLE = "unparsable";

	public interface Parameters extends WorkParameters {
		ConfigurableFileCollection getFiles();

		RegularFileProperty getProfile();

		Property<String> getSourceLevel();

		Property<Boolean> getCheckOnly();

		RegularFileProperty getReport();
	}

	@Override
	public void execute() {
		Parameters p = getParameters();
		String level = p.getSourceLevel().get();
		CodeFormatter formatter = ToolFactory.createCodeFormatter(options(p.getProfile().get().getAsFile(), level));
		List<String> report = new ArrayList<>();
		try {
			for (File file : p.getFiles().getFiles()) {
				if (!file.getName().endsWith(".java")) { continue; }
				String raw = Files.readString(file.toPath());
				boolean crlf = raw.contains("\r\n");
				String src = raw.replace("\r\n", "\n");
				String out = parses(src, level) ? format(formatter, src) : null;
				if (out == null) {
					report.add(UNPARSABLE + "\t" + file.getPath());
					continue;
				}
				if (crlf) { out = out.replace("\n", "\r\n"); }
				if (!out.equals(raw)) {
					report.add(CHANGED + "\t" + file.getPath());
					if (!p.getCheckOnly().get()) { Files.writeString(file.toPath(), out); }
				}
			}
			Files.write(p.getReport().get().getAsFile().toPath(), report);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Eclipse's defaults, the Java version, then the profile's settings on top. */
	static Map<String, String> options(File profile, String level) {
		Map<String, String> options = new HashMap<>(DefaultCodeFormatterConstants.getEclipseDefaultSettings());
		options.put(JavaCore.COMPILER_SOURCE, level);
		options.put(JavaCore.COMPILER_COMPLIANCE, level);
		try {
			NodeList settings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profile).getElementsByTagName("setting");
			for (int i = 0; i < settings.getLength(); i++) {
				Element e = (Element) settings.item(i);
				options.put(e.getAttribute("id"), e.getAttribute("value"));
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("can't read the formatter profile " + profile + ": " + e.getMessage(), e);
		}
		return options;
	}

	/** The source formatted as a whole file with LF line endings, or null if JDT can't. */
	static String format(CodeFormatter formatter, String src) {
		var edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, src, 0, src.length(), 0, "\n");
		if (edit == null) { return null; }
		Document d = new Document(src);
		try {
			edit.apply(d);
		} catch (Exception e) {
			return null;
		}
		return d.get();
	}

	/** Whether the source has no syntax errors. JDT doesn't say: given a syntax error, it returns an edit that changes nothing. */
	static boolean parses(String src, String level) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(level, options);
		options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(src.toCharArray());
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		return Arrays.stream(cu.getProblems()).noneMatch(IProblem::isError);
	}
}
