package black;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The magic-string check's settings for a package, on its {@code package-info.java}, or for the whole module, on its
 * {@code module-info.java}. They go over the build script's {@code javaStandards} settings: a package's apply to it and
 * the packages inside it, and the longest package name wins. Anything left at {@code INHERIT} comes from the package
 * above, the module, or the build script. The plugin reads them from the source, so they cost nothing at run time.
 *
 * <pre>
 * &#64;MagicStrings(level = MagicStrings.Level.WARNING, proximity = MagicStrings.Proximity.FILE)
 * package com.example.cli;
 *
 * import black.MagicStrings;
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.MODULE})
public @interface MagicStrings {
	/**
	 * What a value repeated as an argument is.
	 *
	 * @return an error, a warning, or not checked
	 */
	Level level() default Level.INHERIT;

	/**
	 * The most severe a value that is never an argument can be.
	 *
	 * @return its level
	 */
	Level elsewhere() default Level.INHERIT;

	/**
	 * Whether values with whitespace in them are checked.
	 *
	 * @return check or skip them
	 */
	Whitespace whitespace() default Whitespace.INHERIT;

	/**
	 * How close a value's literals may all sit and go unreported; none by default.
	 *
	 * @return the proximity
	 */
	Proximity proximity() default Proximity.INHERIT;

	/** A level, as {@code javaStandards.magicStrings} spells it in lower case. */
	enum Level {
		/** As the package above, the module, or the build script says. */
		INHERIT,
		/** The build fails. */
		ERROR,
		/** Reported, and the build goes on. */
		WARNING,
		/** Not checked: the package's literals aren't counted at all. */
		IGNORE
	}

	/** Whether values with whitespace in them are checked. */
	enum Whitespace {
		/** As the package above, the module, or the build script says. */
		INHERIT,
		/** Checked like any other value. */
		CHECK,
		/** Skipped: a value with whitespace in it is almost always text for people. */
		SKIP
	}

	/** How close a value's literals must be, narrowest first, as {@code javaStandards.magicStringsProximity} spells it. */
	enum Proximity {
		/** As the package above, the module, or the build script says. */
		INHERIT,
		/** Nothing is close: every repeat is reported. */
		NONE,
		/** One statement, or a {@code case}'s or {@code if}'s head with its first statement. */
		STATEMENT,
		/** One method, constructor, initializer or field. */
		METHOD,
		/** One file. */
		FILE,
		/** One package. */
		PACKAGE,
		/** The whole module: nothing is reported. */
		MODULE
	}
}
