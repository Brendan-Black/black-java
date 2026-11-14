package black.checks;

import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * A compiler error for every constructor parameter of type {@code java.util.Optional}, a record's components included
 * (black style guide, values/no-optional-in-constructor.md). It runs as an annotation processor, so javac and ECJ both report it.
 * It sees every class, interface, enum and record the compiler is given, nested ones included, but not local ones:
 * processors aren't shown what's declared inside a method.
 */
public final class NoOptionalInConstructors extends AbstractProcessor {
	private static final String MESSAGE = "Optional in a constructor%s: take the plain value, marked @Nullable, and return the Optional from an"
		+ " accessor (black style guide, values/no-optional-in-constructor.md)";

	/** Run by the compiler, which finds it by its name in META-INF/services. */
	public NoOptionalInConstructors() {}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of("*");
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
		for (TypeElement type : ElementFilter.typesIn(round.getRootElements())) {
			check(type);
		}
		return false; // claims nothing, so other processors still see every annotation
	}

	private void check(TypeElement type) {
		if (type.getKind() == ElementKind.RECORD) {
			// Reported on the component, naming it and its record: ECJ (3.46) gives no file or line for anything a record
			// declares, so the message has to say where.
			for (var component : type.getRecordComponents()) {
				if (isOptional(component.asType())) { error(component, " (" + type.getQualifiedName() + ", component " + component.getSimpleName() + ")"); }
			}
		}
		for (var constructor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
			if (type.getKind() == ElementKind.RECORD && isCanonical(type, constructor.getParameters())) { continue; } // reported above
			for (VariableElement parameter : constructor.getParameters()) {
				if (isOptional(parameter.asType())) { error(parameter, ""); }
			}
		}
		for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
			check(nested);
		}
	}

	/** Whether these parameters are the record's components, in order: then this is its canonical constructor. */
	private boolean isCanonical(TypeElement record, List<? extends VariableElement> parameters) {
		var components = record.getRecordComponents();
		if (components.size() != parameters.size()) { return false; }
		var types = processingEnv.getTypeUtils();
		for (int i = 0; i < parameters.size(); i++) {
			if (!types.isSameType(types.erasure(components.get(i).asType()), types.erasure(parameters.get(i).asType()))) { return false; }
		}
		return true;
	}

	private static boolean isOptional(TypeMirror type) {
		return type.getKind() == TypeKind.DECLARED
			&& ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().contentEquals("java.util.Optional");
	}

	private void error(Element at, String detail) {
		processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(MESSAGE, detail), at);
	}
}
