package org.expresso;

import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso.types.OneOfExpressionType;

/**
 * A class of expressions, any of which can satisfy this type
 *
 * @param <S> The stream super-type of the expression types
 */
public class ExpressionClass<S extends BranchableStream<?, ?>> extends OneOfExpressionType<S> implements GrammarExpressionType<S> {
	private final String theName;

	/**
	 * @param id The cache ID for the expression
	 * @param name The name of the class
	 * @param members The class's members, sorted by priority, then by order of occurrence
	 */
	public ExpressionClass(int id, String name, List<? extends ConfiguredExpressionType<S>> members) {
		super(id, members, true);
		theName = name;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public List<? extends ConfiguredExpressionType<S>> getComponents() {
		return (List<? extends ConfiguredExpressionType<S>>) super.getComponents();
	}

	@Override
	public String toString() {
		return "Class " + theName;
	}
}
