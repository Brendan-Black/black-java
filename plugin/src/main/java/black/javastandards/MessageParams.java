package black.javastandards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;

/**
 * Which parameters of the source set's own methods and constructors are messages: used only where text for people goes
 * (the arguments of a logging or printing method, an exception's constructor, an {@code assert}'s message, or another
 * method's message parameter). A literal passed to one is a label for an error, like {@code check("JSON", ...)}, not a
 * value the code handles. A parameter used anywhere else (compared, looked up, stored, returned, or formatted into a
 * string that is) isn't one, and neither is any parameter of a method from outside the source set, whose body can't be
 * read.
 * <p>
 * Without types, a call is matched to a declaration by its name and number of arguments (a constructor by its type's
 * simple name). Declarations a call can't tell apart share one entry, which says message only where every one of them
 * does.
 */
final class MessageParams {
	private record Callee(String name, int arity, boolean constructor) {}

	/** A method or constructor with a body: how calls name it, its parameters' names, and its body. */
	private record Declaration(Callee callee, List<String> params, Block body, String type) {}

	/** Methods whose arguments are messages, but which build a string that may be a value: what goes in is used. */
	private static final Set<String> STRING_BUILDERS = Set.of("format", "formatted");
	/** {@code Objects.requireNonNull(value, message)}: only the arguments after the first are messages. */
	private static final String REQUIRE_NON_NULL = "requireNonNull";

	private final Map<Callee, boolean[]> params;

	private MessageParams(Map<Callee, boolean[]> params) {
		this.params = params;
	}

	/**
	 * Works it out for these files: no parameter is a message at first, then each pass marks the ones used only in what is
	 * already known to be a message, until nothing changes. Every pass only adds, so it ends. Starting from nothing keeps a
	 * method from vouching for itself through a call that only shares its name.
	 */
	static MessageParams of(List<CompilationUnit> units) {
		List<Declaration> declarations = new ArrayList<>();
		units.forEach(cu -> cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getBody() != null && !node.isVarargs()) {
					String type = node.getParent() instanceof AbstractTypeDeclaration t ? t.getName().getIdentifier() : null;
					List<String> names = new ArrayList<>();
					for (Object param : node.parameters()) {
						names.add(((SingleVariableDeclaration) param).getName().getIdentifier());
					}
					if (!node.isConstructor() || type != null) {
						String name = node.isConstructor() ? type : node.getName().getIdentifier();
						declarations.add(new Declaration(new Callee(name, names.size(), node.isConstructor()), names, node.getBody(), type));
					}
				}
				return true;
			}
		}));
		MessageParams current = combine(declarations, d -> new boolean[d.params().size()]);
		while (true) {
			MessageParams known = current;
			MessageParams next = combine(declarations, d -> known.messageOnly(d));
			if (next.sameAs(current)) { return current; }
			current = next;
		}
	}

	/** Whether the {@code index}th argument of a call to a method named {@code name} is a message. */
	boolean method(String name, int arity, int index) {
		return flag(new Callee(name, arity, false), index);
	}

	/** Whether the {@code index}th argument of {@code new Type(...)}, by the type's simple name, is a message. */
	boolean constructor(String type, int arity, int index) {
		return flag(new Callee(type, arity, true), index);
	}

	/** {@code new a.b.Type<T>(...)}'s type, as declarations name it: {@code Type}. */
	static String simpleName(Type type) {
		String name = type.toString();
		int generic = name.indexOf('<');
		if (generic >= 0) { name = name.substring(0, generic); }
		return name.substring(name.lastIndexOf('.') + 1);
	}

	private boolean flag(Callee callee, int index) {
		boolean[] flags = params.get(callee);
		return flags != null && flags[index];
	}

	private boolean sameAs(MessageParams other) {
		if (!params.keySet().equals(other.params.keySet())) { return false; }
		return params.entrySet().stream().allMatch(e -> Arrays.equals(e.getValue(), other.params.get(e.getKey())));
	}

	private static MessageParams combine(List<Declaration> declarations, Function<Declaration, boolean[]> each) {
		Map<Callee, boolean[]> out = new HashMap<>();
		for (Declaration d : declarations) {
			boolean[] flags = each.apply(d);
			boolean[] existing = out.putIfAbsent(d.callee(), flags);
			if (existing != null) {
				for (int i = 0; i < existing.length; i++) {
					existing[i] &= flags[i];
				}
			}
		}
		return new MessageParams(out);
	}

	/** For each of the declaration's parameters, whether every use of it in the body is in a message. */
	private boolean[] messageOnly(Declaration d) {
		Set<String> names = new HashSet<>(d.params());
		Set<String> outside = new HashSet<>();
		// A parameter that is never used isn't a message either: nothing shows what it is for.
		Set<String> used = new HashSet<>();
		d.body().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				String id = node.getIdentifier();
				if (!names.contains(id) || !isReference(node)) { return false; }
				used.add(id);
				if (!inMessage(node, d)) { outside.add(id); }
				return false;
			}
		});
		boolean[] flags = new boolean[d.params().size()];
		for (int i = 0; i < flags.length; i++) {
			String name = d.params().get(i);
			flags[i] = used.contains(name) && !outside.contains(name);
		}
		return flags;
	}

	/** Places a name doesn't refer to a variable: a method's, a field's or a type's name, or a declaration. */
	private static final Set<StructuralPropertyDescriptor> NOT_VARIABLES = Set.of(MethodInvocation.NAME_PROPERTY,
		SuperMethodInvocation.NAME_PROPERTY, FieldAccess.NAME_PROPERTY, SuperFieldAccess.NAME_PROPERTY, QualifiedName.NAME_PROPERTY,
		SimpleType.NAME_PROPERTY);

	/** Whether a name may refer to a variable. Unsure counts as yes, which only ever makes a parameter count as used. */
	private static boolean isReference(SimpleName node) {
		return !node.isDeclaration() && !NOT_VARIABLES.contains(node.getLocationInParent());
	}

	/**
	 * Whether a node goes straight into a message: walking up to the body, through parentheses, string concatenation,
	 * {@code format} and calls on the value itself ({@code name.trim()}), it reaches a call that takes it as a message. Passed
	 * to any other call on the way ({@code gson.toJson(item)}), it is a value, whatever is done with the result: a log line
	 * can carry data.
	 */
	private boolean inMessage(ASTNode node, Declaration d) {
		ASTNode child = node;
		for (ASTNode parent = node.getParent(); parent != null && child != d.body(); child = parent, parent = parent.getParent()) {
			if (parent instanceof MethodInvocation call) {
				int index = call.arguments().indexOf(child);
				if (index < 0) { continue; }
				String name = call.getName().getIdentifier();
				if (STRING_BUILDERS.contains(name)) { continue; }
				return name.equals(REQUIRE_NON_NULL) ? index > 0
					: MagicStringsAction.MESSAGE_METHODS.contains(name) || method(name, call.arguments().size(), index);
			} else if (parent instanceof SuperMethodInvocation call) {
				int index = call.arguments().indexOf(child);
				if (index >= 0) { return method(call.getName().getIdentifier(), call.arguments().size(), index); }
			} else if (parent instanceof ClassInstanceCreation creation) {
				int index = creation.arguments().indexOf(child);
				if (index < 0) { continue; }
				String type = simpleName(creation.getType());
				return MagicStringsAction.isExceptionType(type) || constructor(type, creation.arguments().size(), index);
			} else if (parent instanceof ConstructorInvocation call) {
				int index = call.arguments().indexOf(child);
				if (index >= 0) { return d.type() != null && constructor(d.type(), call.arguments().size(), index); }
			} else if (parent instanceof AssertStatement assertion) {
				return child == assertion.getMessage();
			} else if (!(parent instanceof ParenthesizedExpression || parent instanceof CastExpression
				|| parent instanceof InfixExpression infix && infix.getOperator() == InfixExpression.Operator.PLUS
				|| parent instanceof ConditionalExpression conditional && child != conditional.getExpression())) {
				// Stored, returned, compared, assigned, or anything else that isn't on its way into a call.
				return false;
			}
		}
		return false;
	}
}
