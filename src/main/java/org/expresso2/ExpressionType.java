package org.expresso2;

import java.util.List;
import java.util.NavigableSet;

import org.expresso.parse.BranchableStream;

public class ExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpression<S> {
	public final int id;
	public final int priority;

	private final String theName;
	private final NavigableSet<ExpressionClass<S>> theClasses;

	public ExpressionType(int id, int priority, String name, NavigableSet<ExpressionClass<S>> classes,
		List<ExpressionComponent<S>> components) {
		super(id, components);
		this.id = id;
		this.priority = priority;
		theName = name;
		theClasses = classes;
	}

	public String getName() {
		return theName;
	}

	public NavigableSet<ExpressionClass<S>> getClasses() {
		return theClasses;
	}

	@Override
	public String toString() {
		return "Expression " + theName;
	}
}
