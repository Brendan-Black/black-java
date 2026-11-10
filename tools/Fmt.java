import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Formats Java files in place with a profile, the way VS Code's Java extension would, keeping each file's line
 * endings. A file that comes out the same is left alone. Exits with 1 if any file can't be formatted.
 *
 * <pre>
 * java -cp "JARS" tools/Fmt.java java-formatter.xml File.java...
 * </pre>
 */
public class Fmt {
	public static void main(String[] args) throws Exception {
		CodeFormatter f = ToolFactory.createCodeFormatter(options(Path.of(args[0])));
		int failed = 0;
		for (int i = 1; i < args.length; i++) {
			Path p = Path.of(args[i]);
			String raw = Files.readString(p);
			boolean crlf = raw.contains("\r\n");
			String out = format(f, raw.replace("\r\n", "\n"));
			if (out == null) {
				System.err.println("can't format (doesn't parse?): " + p);
				failed++;
				continue;
			}
			if (crlf) { out = out.replace("\n", "\r\n"); }
			if (!out.equals(raw)) { Files.writeString(p, out); }
		}
		if (failed > 0) { System.exit(1); }
	}

	/** Eclipse's defaults, Java 25, then the profile's settings on top. */
	static Map<String, String> options(Path profile) throws Exception {
		Map<String, String> options = new HashMap<>(DefaultCodeFormatterConstants.getEclipseDefaultSettings());
		options.put(JavaCore.COMPILER_SOURCE, "25");
		options.put(JavaCore.COMPILER_COMPLIANCE, "25");
		NodeList settings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profile.toFile()).getElementsByTagName("setting");
		for (int i = 0; i < settings.getLength(); i++) {
			Element e = (Element) settings.item(i);
			options.put(e.getAttribute("id"), e.getAttribute("value"));
		}
		return options;
	}

	/**
	 * The source formatted as a whole file with LF line endings, or null if it doesn't parse. (JDT doesn't say: given
	 * code with a syntax error, it returns an edit that changes nothing.)
	 */
	static String format(CodeFormatter f, String src) throws Exception {
		if (!parses(src)) { return null; }
		var edit = f.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, src, 0, src.length(), 0, "\n");
		if (edit == null) { return null; }
		Document d = new Document(src);
		edit.apply(d);
		return d.get();
	}

	/** Whether the source has no syntax errors, as Java 25. */
	static boolean parses(String src) {
		ASTParser p = ASTParser.newParser(AST.getJLSLatest());
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions("25", options);
		options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		p.setCompilerOptions(options);
		p.setKind(ASTParser.K_COMPILATION_UNIT);
		p.setSource(src.toCharArray());
		CompilationUnit cu = (CompilationUnit) p.createAST(null);
		return Arrays.stream(cu.getProblems()).noneMatch(IProblem::isError);
	}
}
