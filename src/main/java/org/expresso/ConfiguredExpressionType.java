package org.expresso;

import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso.types.SequenceExpressionType;
import org.qommons.collect.BetterSortedSet;

/**
 * Represents an expression type that was configured as a named type in a grammar file
 *
 * @param <S> The stream super-type of the expression
 */
public class ConfiguredExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpressionType<S>
	implements GrammarExpressionType<S> {
	private final ExpressoGrammar<S> theGrammar;
	private final String theName;
	private final int thePriority;
	private final BetterSortedSet<ExpressionClass<S>> theClasses;

	/**
	 * @param grammar The grammar that this expression type belongs to
	 * @param id The cache ID of this type
	 * @param priority The priority of the type
	 * @param name The name of the type
	 * @param classes The classes the type belongs to
	 * @param components The sequence of components composing this type
	 */
	public ConfiguredExpressionType(ExpressoGrammar<S> grammar, int id, int priority, String name,
		BetterSortedSet<ExpressionClass<S>> classes, List<ExpressionType<S>> components) {
		super(id, components);
		theGrammar = grammar;
		thePriority = priority;
		theName = name;
		theClasses = classes;
	}

	@Override
	public ExpressoGrammar<S> getGrammar() {
		return theGrammar;
	}

	@Override
	public String getName() {
		return theName;
	}

	/** @return The priority of this type over others in the same class */
	public int getPriority() {
		return thePriority;
	}

	/** @return All classes that this type belongs to */
	public BetterSortedSet<ExpressionClass<S>> getClasses() {
		return theClasses;
	}

	/**
	 * @param clazz The class to test
	 * @return The class that this expression type belongs to that extends the given class, or null if there is none
	 */
	public ExpressionClass<S> getExtension(ExpressionClass<S> clazz) {
		for (ExpressionClass<S> c : theClasses) {
			if (c.doesExtend(clazz))
				return c;
		}
		return null;
	}

	@Override
	public String toString() {
		return "Expression " + theName;
	}
}
