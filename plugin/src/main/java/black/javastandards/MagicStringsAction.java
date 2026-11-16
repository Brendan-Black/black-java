package black.javastandards;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/**
 * Finds every string value that appears in more than one literal (black style guide, values/magic-strings.md). Values match
 * only when identical, after escapes are resolved: {@code "orderId"} matches {@code "orderId"}, not
 * {@code "orderid"} or {@code "orderIdList"}.
 * <p>
 * Each literal is one of three kinds. An <em>argument</em> is passed straight to a method or constructor
 * ({@code get("id")}, {@code List.of("a")}, and the receiver of {@code "SHIPPED".equals(s)}): the biggest offenders, so a
 * value with an argument among its literals gets its package's full level. A <em>definition</em> is in a {@code static final}
 * field (an interface's included) or an enum constant's arguments: a value whose literals are all definitions is two names
 * that happen to share a spelling, and isn't reported. Everything else ({@code case} labels, assignments, returns) is
 * <em>elsewhere</em>, and a value seen only there and in definitions is capped at the {@code magicStringsElsewhere} level. A
 * literal a value is built from ({@code Optional.of("x")}, a record of the source set) is an argument only if that value is
 * passed on.
 * <p>
 * Two shapes are eased. A value whose literals are all in one method (or other member), with at most one of them an
 * argument, isn't reported: a parser's {@code startsWith("--x=")} beside its {@code case "--x="}. And a value passed as
 * an argument just once, with every other use in other files (or in that same method), is at most a warning: most likely
 * another concept that shares the spelling.
 * <p>
 * Not counted at all: values with no letter ({@code ", "}, {@code "\n"}, {@code "404"}); values with whitespace in them, unless
 * {@code magicStringsWhitespace} is true; messages (the arguments of logging and
 * printing methods, {@code format}, {@code formatted}, {@code requireNonNull}, an exception's constructor, and an
 * {@code assert}); labels (a literal passed straight to a parameter of the source set's own methods that only reaches a
 * message: see {@link MessageParams}); annotations; generated files (under the build directory, annotated {@code @Generated}, or opening with a
 * comment line that starts {@code @generated}); and any file in a package whose level is {@code ignore}.
 * <p>
 * Writes the findings to the report: per value, a line {@code <level>\t<message>}, then a line {@code \t<place>} for each
 * literal. Runs in a worker whose class loader has JDT.
 */
public abstract class MagicStringsAction implements WorkAction<MagicStringsAction.Parameters> {
	public interface Parameters extends WorkParameters {
		ConfigurableFileCollection getFiles();

		Property<String> getSourceLevel();

		/** The level for packages that {@link #getLevelByPackage()} doesn't name. */
		Property<String> getLevel();

		/** Package name → level, for that package and the ones below it. The longest match wins. */
		MapProperty<String, String> getLevelByPackage();

		/** The most severe a value that is never an argument can be. */
		Property<String> getElsewhere();

		/** Whether values with whitespace in them are counted. */
		Property<Boolean> getWhitespace();

		/** How close a value's literals must all be to be read together: see {@link MagicStringsPolicy.Proximity}. */
		Property<String> getProximity();

		/** Files under it are generated, so not read. */
		DirectoryProperty getBuildDir();

		DirectoryProperty getBaseDir();

		RegularFileProperty getReport();
	}

	/** Methods whose arguments are messages for people. */
	static final Set<String> MESSAGE_METHODS = Set.of("trace", "debug", "info", "warn", "warning", "error", "fatal", "log", "lifecycle",
		"quiet", "print", "println", "printf", "format", "formatted", "requireNonNull");
	/** {@code Optional}'s factories, which wrap a value: {@code Optional.of("x")} passes "x" on only if it is passed on. */
	private static final Set<String> OPTIONAL_FACTORIES = Set.of("of", "ofNullable");
	private static final String OPTIONAL = "Optional";

	/** Methods whose literal receiver is compared with the argument, so is as much an argument: {@code "SHIPPED".equals(s)}. */
	private static final Set<String> COMPARISONS = Set.of("equals", "equalsIgnoreCase", "contentEquals", "compareTo", "compareToIgnoreCase");

	/** The simple names of the records these files declare. */
	private static Set<String> recordNames(Collection<CompilationUnit> units) {
		Set<String> names = new HashSet<>();
		units.forEach(cu -> cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(RecordDeclaration node) {
				names.add(node.getName().getIdentifier());
				return true;
			}
		}));
		return names;
	}

	/** A name's last part: {@code Optional} for {@code java.util.Optional}. */
	private static String lastName(Name name) {
		return name instanceof QualifiedName q ? q.getName().getIdentifier() : ((SimpleName) name).getIdentifier();
	}

	/** Whether a type, by name, is an exception, whose constructor's arguments are its message. */
	static boolean isExceptionType(String type) {
		return type.endsWith("Exception") || type.endsWith("Error");
	}

	enum Kind {
		ARGUMENT, DEFINITION, ELSEWHERE
	}

	/**
	 * A literal: its file and package, where in the file, its package's settings, its kind, and the member (method, field)
	 * and the unit (statement, or branch head with its first statement) it is in.
	 */
	private record Place(String file, String pkg, String where, MagicStringsPolicy.Resolved settings, Kind kind, int scope, int unit) {}

	/** How many methods a value never passed as an argument must be in to be the same check written again. */
	private static final int SPREAD = 3;

	/** Whether a value's literals are all this close. */
	private static boolean close(List<Place> places, MagicStringsPolicy.Proximity proximity) {
		Place first = places.get(0);
		return switch (proximity) {
			case STATEMENT -> places.stream().allMatch(place -> place.unit() == first.unit());
			case METHOD -> places.stream().allMatch(place -> place.scope() == first.scope());
			case FILE -> places.stream().allMatch(place -> place.file().equals(first.file()));
			case PACKAGE -> places.stream().allMatch(place -> place.pkg().equals(first.pkg()));
			case MODULE -> true;
		};
	}

	@Override
	public void execute() {
		Parameters p = getParameters();
		var project = new MagicStringsPolicy.Settings(Level.parse(p.getLevel().get()), Level.parse(p.getElsewhere().get()), p.getWhitespace().get(),
			MagicStringsPolicy.Proximity.parse(p.getProximity().get()));
		Map<String, Level> byPackage = new TreeMap<>();
		p.getLevelByPackage().get().forEach((pkg, level) -> byPackage.put(pkg, Level.parse(level)));
		Path base = p.getBaseDir().get().getAsFile().toPath();
		Path build = p.getBuildDir().get().getAsFile().toPath();

		Map<String, List<Place>> seen = new TreeMap<>();
		try {
			Map<File, CompilationUnit> units = new TreeMap<>();
			for (File file : p.getFiles().getFiles()) {
				if (!file.getName().endsWith(".java") || file.toPath().startsWith(build)) { continue; }
				String src = Files.readString(file.toPath());
				if (opensWithGeneratedMarker(src)) { continue; }
				CompilationUnit cu = parse(src, p.getSourceLevel().get(), file.getName());
				if (!isAnnotatedGenerated(cu)) { units.put(file, cu); }
			}
			MessageParams messages = MessageParams.of(List.copyOf(units.values()));
			Map<String, CompilationUnit> byName = new TreeMap<>();
			units.forEach((file, cu) -> byName.put(base.relativize(file.toPath()).toString(), cu));
			MagicStringsPolicy policy = MagicStringsPolicy.of(project, byPackage, byName);
			Set<String> records = recordNames(units.values());
			int[] scopes = {0};
			for (Map.Entry<File, CompilationUnit> unit : units.entrySet()) {
				CompilationUnit cu = unit.getValue();
				String pkg = cu.getPackage() == null ? "" : cu.getPackage().getName().getFullyQualifiedName();
				MagicStringsPolicy.Resolved settings = policy.resolve(pkg);
				if (settings.level() == Level.IGNORE) { continue; }
				cu.accept(new Finder(cu, base.relativize(unit.getKey().toPath()).toString(), settings, messages, records, scopes, seen));
			}
			List<String> report = new ArrayList<>();
			seen.forEach((value, places) -> {
				if (places.size() < 2 || places.stream().allMatch(place -> place.kind() == Kind.DEFINITION)) { return; }
				List<Place> passed = places.stream().filter(place -> place.kind() == Kind.ARGUMENT).toList();
				long arguments = passed.size();
				// One method's literals, with at most one of them an argument, are read together and change together: a
				// parser's startsWith("--x=") beside its case "--x=". Two arguments (declaring a flag and looking it up) are the
				// typo the rule is for.
				boolean defined = places.stream().anyMatch(place -> place.kind() == Kind.DEFINITION);
				if (arguments <= 1 && !defined && places.stream().allMatch(place -> place.scope() == places.get(0).scope())) { return; }
				// Literals this close, however many are arguments, are read together: a case's startsWith("--x") and its
				// value("--x"). Not with a constant among them: a constant plus a literal should use the constant. A value across
				// packages takes the narrowest proximity and the most severe levels among them.
				var proximity = places.stream().map(place -> place.settings().proximity()).min(Enum::compareTo).orElseThrow();
				if (!defined && close(places, proximity)) { return; }
				Level asArgument = places.stream().map(place -> place.settings().level()).min(Enum::compareTo).orElseThrow();
				Level level = asArgument;
				if (arguments == 0) {
					level = places.stream().map(place -> place.settings().elsewhere()).min(Enum::compareTo).orElseThrow();
					// Compared or matched in several methods, a value is the same check written again each time (four parsers of
					// "true", "yes", "on"): at least a warning, though it is never passed on.
					long methods = places.stream().filter(place -> place.kind() != Kind.DEFINITION).mapToInt(Place::scope).distinct().count();
					Level spread = Level.leastSevere(asArgument, Level.WARNING);
					if (methods >= SPREAD && spread.compareTo(level) < 0) { level = spread; }
				}
				// One argument, every other use in another file or in its own method: most likely another concept that shares
				// the spelling, such as one flag in two commands' parsers, so at most a warning. Never with a constant among them:
				// the name exists, so the literal is wrong wherever it is.
				if (arguments == 1 && !defined) {
					Place argument = passed.get(0);
					if (places.stream().allMatch(place -> place.scope() == argument.scope() || !place.file().equals(argument.file()))) {
						level = Level.leastSevere(level, Level.WARNING);
					}
				}
				if (level == Level.IGNORE) { return; }
				report.add(level.word + "\tmagic string " + quote(value) + " appears " + places.size() + " times, " + arguments
					+ " as an argument: name it (black style guide, values/magic-strings.md)");
				// First the places that make it this level: the arguments and the other uses in their files. Uses in files with no
				// argument follow, by file.
				Set<String> argumentFiles = new HashSet<>();
				passed.forEach(place -> argumentFiles.add(place.file()));
				Map<String, Integer> rest = new TreeMap<>();
				for (Place place : places) {
					if (arguments == 0 || argumentFiles.contains(place.file())) {
						report.add("\t" + place.where() + " (" + place.kind().name().toLowerCase(Locale.ROOT) + ")");
					} else {
						rest.merge(place.file(), 1, Integer::sum);
					}
				}
				if (!rest.isEmpty()) {
					List<String> listed = new ArrayList<>();
					rest.forEach((file, n) -> listed.add(file + " (" + n + ")"));
					report.add("\talso, not passed as an argument there: " + String.join(", ", listed));
				}
			});
			Files.write(p.getReport().get().getAsFile().toPath(), report);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Records every counted literal in one file, with its kind. */
	private static final class Finder extends ASTVisitor {
		private final CompilationUnit cu;
		private final String name;
		/** The settings of the file's package. */
		private final MagicStringsPolicy.Resolved settings;
		/** The file's package, "" for the unnamed one. */
		private final String pkg;
		/** Which parameters of the source set's own methods and constructors are messages. */
		private final MessageParams messages;
		/** The simple names of the source set's records: {@code new R("x")} builds a value, like {@code Optional.of("x")}. */
		private final Set<String> records;
		/** How many scopes the project has had, so each method (or other member) gets its own number. */
		private final int[] scopes;
		/** The scope being visited, and the ones it is inside. */
		private int scope;
		private final ArrayDeque<Integer> outerScopes = new ArrayDeque<>();
		/** The unit (statement, or branch head with its first statement) being visited, and the ones it is inside. */
		private int unit;
		private final ArrayDeque<Integer> outerUnits = new ArrayDeque<>();
		/** A branch head's unit, which the next statement (the first of its body) joins; 0 when there is none. */
		private int joinUnit;
		private final Map<String, List<Place>> seen;
		/** Literals passed straight to a call, found when the call is visited, before its arguments are. */
		private final Set<ASTNode> arguments = Collections.newSetFromMap(new IdentityHashMap<>());
		/** How many definitions (static final fields, enum constants) the visitor is inside. */
		private int definitions;

		Finder(CompilationUnit cu, String name, MagicStringsPolicy.Resolved settings, MessageParams messages, Set<String> records, int[] scopes,
			Map<String, List<Place>> seen) {
			this.records = records;
			this.pkg = cu.getPackage() == null ? "" : cu.getPackage().getName().getFullyQualifiedName();
			this.scopes = scopes;
			this.scope = ++scopes[0];
			this.unit = ++scopes[0];
			this.messages = messages;
			this.cu = cu;
			this.name = name;
			this.settings = settings;
			this.seen = seen;
		}

		@Override
		public boolean visit(StringLiteral node) {
			return record(node.getLiteralValue(), node);
		}

		@Override
		public boolean visit(TextBlock node) {
			return record(node.getLiteralValue(), node);
		}

		@Override
		public boolean visit(MethodInvocation node) {
			String method = node.getName().getIdentifier();
			if (MESSAGE_METHODS.contains(method)) {
				// A message: its arguments aren't read, nor is a literal receiver ("%s: %s".formatted(...)).
				Expression receiver = node.getExpression();
				if (receiver != null && !isLiteral(receiver)) { receiver.accept(this); }
				return false;
			}
			// A value wrapped (Optional.of("x")) is only passed on if the wrapper is, which markArgument sees from outside.
			if (isWrapper(node)) { return true; }
			if (COMPARISONS.contains(method) && node.getExpression() != null) { markArgument(node.getExpression()); }
			int arity = node.arguments().size();
			return call(node.getExpression(), node.arguments(), i -> messages.method(node.getExpression(), method, arity, i));
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			String method = node.getName().getIdentifier();
			int arity = node.arguments().size();
			return call(null, node.arguments(), i -> messages.method(null, method, arity, i));
		}

		@Override
		public boolean visit(ClassInstanceCreation node) {
			if (isExceptionType(node.getType().toString())) {
				// An exception's arguments are its message; an anonymous subclass's body is still code.
				if (node.getAnonymousClassDeclaration() != null) { node.getAnonymousClassDeclaration().accept(this); }
				return false;
			}
			if (isWrapper(node)) { return true; }
			String type = MessageParams.simpleName(node.getType());
			int arity = node.arguments().size();
			if (!call(node.getExpression(), node.arguments(), i -> messages.constructor(type, arity, i))) {
				if (node.getAnonymousClassDeclaration() != null) { node.getAnonymousClassDeclaration().accept(this); }
				return false;
			}
			return true;
		}

		@Override
		public boolean visit(ConstructorInvocation node) {
			ASTNode type = node.getParent();
			while (type != null && !(type instanceof AbstractTypeDeclaration)) { type = type.getParent(); }
			String name = type == null ? null : ((AbstractTypeDeclaration) type).getName().getIdentifier();
			int arity = node.arguments().size();
			return call(null, node.arguments(), i -> name != null && messages.constructor(name, arity, i));
		}

		@Override
		public boolean visit(SuperConstructorInvocation node) {
			markArguments(node.arguments());
			return true;
		}

		@Override
		public boolean visit(AssertStatement node) {
			node.getExpression().accept(this);
			return false; // its message isn't read
		}

		@Override
		public boolean visit(FieldDeclaration node) {
			enterScope();
			if (isConstant(node)) { definitions++; }
			return true;
		}

		@Override
		public void endVisit(FieldDeclaration node) {
			if (isConstant(node)) { definitions--; }
			leaveScope();
		}

		// Each method, initializer and field is a scope of its own; a lambda or local class is in its method's, except
		// for the methods and fields it declares.
		@Override
		public boolean visit(MethodDeclaration node) {
			enterScope();
			return true;
		}

		@Override
		public void endVisit(MethodDeclaration node) {
			leaveScope();
		}

		@Override
		public boolean visit(Initializer node) {
			enterScope();
			return true;
		}

		@Override
		public void endVisit(Initializer node) {
			leaveScope();
		}

		/** A member (method, initializer, field, enum constant) is a scope of its own, and a unit of its own. */
		private void enterScope() {
			outerScopes.push(scope);
			scope = ++scopes[0];
			outerUnits.push(unit);
			unit = ++scopes[0];
			joinUnit = 0;
		}

		private void leaveScope() {
			scope = outerScopes.pop();
			unit = outerUnits.pop();
		}

		// Statements and branches, for the "statement" proximity. A branch's head (a case's labels, an if's condition) is one
		// unit with the first statement it leads to, so `case "--x" -> value("--x")` is one however the formatter breaks its
		// lines; every later statement is a unit of its own, so declaring a flag and then looking it up are two. A block is no
		// unit: its statements are.
		@Override
		public boolean preVisit2(ASTNode node) {
			if (node instanceof Statement && !(node instanceof Block)) {
				outerUnits.push(unit);
				unit = joinUnit != 0 && !(node instanceof SwitchCase) ? joinUnit : ++scopes[0];
				joinUnit = 0;
			}
			return true;
		}

		@Override
		public void postVisit(ASTNode node) {
			if (node instanceof Statement && !(node instanceof Block)) {
				int own = unit;
				unit = outerUnits.pop();
				// What follows a case's labels is the case's body: its first statement joins the case.
				joinUnit = node instanceof SwitchCase ? own : 0;
			}
		}

		@Override
		public boolean visit(IfStatement node) {
			node.getExpression().accept(this);
			// The condition with the first statement of its branch; an else is a branch of its own.
			joinUnit = unit;
			node.getThenStatement().accept(this);
			joinUnit = 0;
			if (node.getElseStatement() != null) { node.getElseStatement().accept(this); }
			return false;
		}

		@Override
		public boolean visit(EnumConstantDeclaration node) {
			enterScope();
			definitions++;
			for (Object argument : node.arguments()) {
				((ASTNode) argument).accept(this);
			}
			definitions--;
			leaveScope();
			// The constant's body, if it has one, is ordinary code.
			if (node.getAnonymousClassDeclaration() != null) { node.getAnonymousClassDeclaration().accept(this); }
			return false;
		}

		// An annotation's arguments are settings for a tool, not values the code handles: @SuppressWarnings("unchecked").
		@Override
		public boolean visit(NormalAnnotation node) {
			return false;
		}

		@Override
		public boolean visit(SingleMemberAnnotation node) {
			return false;
		}

		@Override
		public boolean visit(MarkerAnnotation node) {
			return false;
		}

		/**
		 * A call's arguments are arguments, except a literal at a message parameter, which isn't read. Returns whether the visitor
		 * should go on into the call as usual: when none is a message. Otherwise this visits the receiver and the rest itself.
		 */
		private boolean call(Expression receiver, List<?> args, IntPredicate atMessage) {
			// Only a literal passed straight to a message parameter is a label. Anything else there (a map, a call) still
			// holds values of its own, whatever the method does with it.
			IntPredicate isMessage = i -> atMessage.test(i) && isLiteral(unparenthesized((Expression) args.get(i)));
			if (IntStream.range(0, args.size()).noneMatch(isMessage)) {
				markArguments(args);
				return true;
			}
			if (receiver != null) { receiver.accept(this); }
			for (int i = 0; i < args.size(); i++) {
				if (isMessage.test(i)) { continue; }
				Expression arg = (Expression) args.get(i);
				markArgument(arg);
				arg.accept(this);
			}
			return false;
		}

		private static Expression unparenthesized(Expression e) {
			return e instanceof ParenthesizedExpression paren ? unparenthesized(paren.getExpression()) : e;
		}

		private void markArguments(List<?> expressions) {
			expressions.forEach(e -> markArgument((Expression) e));
		}

		/** A literal passed to a call is an argument, also inside parentheses and array initializers. */
		private void markArgument(Expression e) {
			if (isLiteral(e)) {
				arguments.add(e);
			} else if (e instanceof ParenthesizedExpression paren) {
				markArgument(paren.getExpression());
			} else if (e instanceof ArrayCreation array && array.getInitializer() != null) {
				markArgument(array.getInitializer());
			} else if (e instanceof ArrayInitializer init) {
				markArguments(init.expressions());
			} else if (isWrapper(e)) {
				// new R("x") or Optional.of("x") passed on is still passed on.
				markArguments(e instanceof MethodInvocation call ? call.arguments() : ((ClassInstanceCreation) e).arguments());
			}
		}

		/**
		 * Whether an expression builds a value rather than doing something with one: a record of the source set's
		 * ({@code new Mode("hard")}), or {@code Optional.of} and {@code ofNullable}. A literal it wraps is passed on only if
		 * the value it builds is, so {@code return Optional.of("hard")} is no argument.
		 */
		private boolean isWrapper(Expression e) {
			if (e instanceof ClassInstanceCreation creation) {
				return creation.getAnonymousClassDeclaration() == null && records.contains(MessageParams.simpleName(creation.getType()));
			}
			return e instanceof MethodInvocation call && OPTIONAL_FACTORIES.contains(call.getName().getIdentifier())
				&& call.getExpression() instanceof Name type && OPTIONAL.equals(lastName(type));
		}

		/** Whether a value has a letter in it, not counting the one after a backslash: {@code "\\t"} spelled out is an escape. */
		private static boolean hasLetter(String value) {
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				if (c == '\\') {
					i++;
				} else if (Character.isLetter(c)) {
					return true;
				}
			}
			return false;
		}

		private boolean record(String value, ASTNode node) {
			if (hasLetter(value) && (settings.whitespace() || value.codePoints().noneMatch(Character::isWhitespace))) {
				Kind kind = definitions > 0 ? Kind.DEFINITION : arguments.contains(node) ? Kind.ARGUMENT : Kind.ELSEWHERE;
				int offset = node.getStartPosition();
				String where = name + ":" + cu.getLineNumber(offset) + ":" + (cu.getColumnNumber(offset) + 1);
				seen.computeIfAbsent(value, v -> new ArrayList<>()).add(new Place(name, pkg, where, settings, kind, scope, unit));
			}
			return false;
		}

		/** A static final field, or any field of an interface, which is implicitly static final. */
		private static boolean isConstant(FieldDeclaration node) {
			int modifiers = node.getModifiers();
			boolean inInterface = node.getParent() instanceof TypeDeclaration type && type.isInterface();
			return inInterface || (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
		}
	}

	private static boolean isLiteral(Expression e) {
		return e instanceof StringLiteral || e instanceof TextBlock;
	}

	/** Whether a line of the comments that open the file starts with {@code @generated}, the marker code generators write. */
	static boolean opensWithGeneratedMarker(String src) {
		for (String line : src.lines().map(String::strip).toList()) {
			boolean comment = line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*");
			if (!comment) { return false; }
			if (line.replaceFirst("^[/*\\s]+", "").startsWith("@generated")) { return true; }
		}
		return false;
	}

	/** Whether a top-level type carries an annotation named Generated, from whichever package. */
	private static boolean isAnnotatedGenerated(CompilationUnit cu) {
		for (Object type : cu.types()) {
			for (Object modifier : ((AbstractTypeDeclaration) type).modifiers()) {
				if (modifier instanceof Annotation a) {
					String annotation = a.getTypeName().getFullyQualifiedName();
					if (annotation.equals("Generated") || annotation.endsWith(".Generated")) { return true; }
				}
			}
		}
		return false;
	}

	/** Syntax only, no bindings: a file with errors still gives a tree, and the compiler reports the errors. */
	private static CompilationUnit parse(String src, String level, String fileName) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(level, options);
		options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(src.toCharArray());
		// module-info.java parses as a module declaration only under its own name.
		parser.setUnitName(fileName);
		return (CompilationUnit) parser.createAST(null);
	}

	/** The value as a Java string literal, so whitespace and control characters show. */
	private static String quote(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < ' ') {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}

	/** How a package treats the check, most severe first. */
	enum Level {
		ERROR("error"), WARNING("warning"), IGNORE("ignore");

		final String word;

		Level(String word) {
			this.word = word;
		}

		static Level leastSevere(Level a, Level b) {
			return a.compareTo(b) >= 0 ? a : b;
		}

		static Level parse(String word) {
			for (Level level : values()) {
				if (level.word.equals(word)) { return level; }
			}
			throw new IllegalArgumentException("a javaStandards magic-strings level is \"" + word + "\"; expected \"error\", \"warning\" or \"ignore\"");
		}
	}
}
