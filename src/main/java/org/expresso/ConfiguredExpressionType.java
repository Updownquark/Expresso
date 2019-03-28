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
	private final int thePriority;

	private final String theName;
	private final BetterSortedSet<ExpressionClass<S>> theClasses;

	/**
	 * @param id The cache ID of this type
	 * @param priority The priority of the type
	 * @param name The name of the type
	 * @param classes The classes the type belongs to
	 * @param components The sequence of components composing this type
	 */
	public ConfiguredExpressionType(int id, int priority, String name, BetterSortedSet<ExpressionClass<S>> classes,
		List<ExpressionType<S>> components) {
		super(id, components);
		this.thePriority = priority;
		theName = name;
		theClasses = classes;
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

	@Override
	public String toString() {
		return "Expression " + theName;
	}
}
