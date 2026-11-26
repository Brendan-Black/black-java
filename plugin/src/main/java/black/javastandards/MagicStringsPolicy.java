package black.javastandards;

import black.javastandards.MagicStringsAction.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * The magic-string settings each package is checked by: the project's ({@code javaStandards}), over which go the
 * module's ({@code @MagicStrings} on {@code module-info.java}'s module), then a package's and the packages' it is inside:
 * {@code magicStringsByPackage} and {@code @MagicStrings} on a {@code package-info.java}. For packages the longest name
 * wins, for each setting it sets; at the same name, {@code package-info.java} wins over the build script.
 */
final class MagicStringsPolicy {
	/** How close a value's literals must all be to be read together, narrowest first. */
	enum Proximity {
		/** Nothing is close: every repeat is reported. */
		NONE("none"),
		/**
		 * One statement, or a branch's head with what it leads to: a {@code case}'s labels with its first statement, an
		 * {@code if}'s condition with its first statement. Line breaks don't matter.
		 */
		STATEMENT("statement"),
		/** One method, constructor, initializer or field. */
		METHOD("method"),
		FILE("file"),
		PACKAGE("package"),
		/** The module, which is the whole source set: nothing is reported. */
		MODULE("module");

		final String word;

		Proximity(String word) {
			this.word = word;
		}

		static Proximity parse(String word) {
			for (Proximity p : values()) {
				if (p.word.equals(word)) { return p; }
			}
			throw new IllegalArgumentException("a javaStandards magic-strings proximity is \"" + word
				+ "\"; expected \"none\", \"statement\", \"method\", \"file\", \"package\" or \"module\"");
		}
	}

	/** Settings as one place gives them; a null one is left to the place above. */
	record Settings(Level level, Level elsewhere, Boolean whitespace, Proximity proximity) {
		static final Settings NONE = new Settings(null, null, null, null);

		/** These, with what they leave unset taken from {@code outer}. */
		Settings or(Settings outer) {
			return new Settings(level != null ? level : outer.level, elsewhere != null ? elsewhere : outer.elsewhere,
				whitespace != null ? whitespace : outer.whitespace, proximity != null ? proximity : outer.proximity);
		}
	}

	/** The settings a literal is checked by, everything decided; {@code elsewhere} is already capped at {@code level}. */
	record Resolved(Level level, Level elsewhere, boolean whitespace, Proximity proximity) {}

	/** A package's settings, from the build script (rank 0) or its package-info.java (rank 1). */
	private record ForPackage(String pkg, Settings settings, int rank) {}

	/** The annotation's simple name, as {@code lib}'s {@code black.MagicStrings} declares it. */
	private static final String ANNOTATION = "MagicStrings";
	private static final String INHERIT = "INHERIT";

	private final Settings project;
	private final Settings module;
	/** Shortest package name first, the build script before package-info.java, so later ones go over earlier ones. */
	private final List<ForPackage> packages;

	private MagicStringsPolicy(Settings project, Settings module, List<ForPackage> packages) {
		this.project = project;
		this.module = module;
		this.packages = packages;
	}

	/** The policy for these files, by the project's settings and its levels by package. */
	static MagicStringsPolicy of(Settings project, Map<String, Level> byPackage, Map<String, CompilationUnit> units) {
		Settings module = Settings.NONE;
		List<ForPackage> packages = new ArrayList<>();
		byPackage.forEach((pkg, level) -> packages.add(new ForPackage(pkg, new Settings(level, null, null, null), 0)));
		for (Map.Entry<String, CompilationUnit> unit : units.entrySet()) {
			CompilationUnit cu = unit.getValue();
			if (cu.getModule() != null) {
				module = settingsIn(cu.getModule().annotations(), unit.getKey()).or(module);
			} else if (cu.getPackage() != null && cu.types().isEmpty()) {
				// package-info.java: a package declaration and nothing else.
				Settings settings = settingsIn(cu.getPackage().annotations(), unit.getKey());
				packages.add(new ForPackage(cu.getPackage().getName().getFullyQualifiedName(), settings, 1));
			}
		}
		packages.sort(Comparator.comparingInt((ForPackage f) -> f.pkg().length()).thenComparingInt(ForPackage::rank));
		return new MagicStringsPolicy(project, module, packages);
	}

	/** The settings for a package: its own, then the packages' it is inside, the module's and the project's. */
	Resolved resolve(String pkg) {
		Settings settings = module.or(project);
		for (ForPackage f : packages) {
			if (pkg.equals(f.pkg()) || pkg.startsWith(f.pkg() + ".")) { settings = f.settings().or(settings); }
		}
		Level level = settings.level();
		return new Resolved(level, Level.leastSevere(level, settings.elsewhere()), settings.whitespace(), settings.proximity());
	}

	/** What {@code @MagicStrings(...)} among these annotations sets; {@code where} names the file in errors. */
	private static Settings settingsIn(List<?> annotations, String where) {
		Settings settings = Settings.NONE;
		for (Object a : annotations) {
			if (!(a instanceof Annotation annotation) || !ANNOTATION.equals(lastName(annotation.getTypeName()))) { continue; }
			if (!(annotation instanceof NormalAnnotation normal)) { continue; }
			Level level = null, elsewhere = null;
			Boolean whitespace = null;
			Proximity proximity = null;
			for (Object v : normal.values()) {
				MemberValuePair pair = (MemberValuePair) v;
				String constant = constantName(pair.getValue());
				if (constant == null) {
					throw new IllegalArgumentException(where + ": @MagicStrings(" + pair.getName() + " = ...) takes a constant, such as Level.WARNING");
				}
				if (constant.equals(INHERIT)) { continue; }
				String word = constant.toLowerCase(Locale.ROOT);
				try {
					switch (pair.getName().getIdentifier()) {
						case "level" -> level = Level.parse(word);
						case "elsewhere" -> elsewhere = Level.parse(word);
						case "whitespace" -> whitespace = constant.equals("CHECK");
						case "proximity" -> proximity = Proximity.parse(word);
						default -> throw new IllegalArgumentException("@MagicStrings has no " + pair.getName());
					}
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
				}
			}
			settings = new Settings(level, elsewhere, whitespace, proximity);
		}
		return settings;
	}

	/** {@code Level.WARNING}, {@code MagicStrings.Level.WARNING} or a statically imported {@code WARNING}: its last name. */
	private static String constantName(Expression e) {
		if (e instanceof Name name) { return lastName(name); }
		if (e instanceof FieldAccess access) { return access.getName().getIdentifier(); }
		return null;
	}

	private static String lastName(Name name) {
		return name instanceof QualifiedName q ? q.getName().getIdentifier() : ((SimpleName) name).getIdentifier();
	}
}
