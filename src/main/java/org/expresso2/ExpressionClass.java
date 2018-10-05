package org.expresso2;

import java.util.List;

import org.expresso.parse.BranchableStream;

public class ExpressionClass<S extends BranchableStream<?, ?>> extends OneOfExpression<S> {
	private final String theName;

	public ExpressionClass(int id, String name, List<? extends ExpressionType<S>> members) {
		super(id, members);
		theName = name;
	}

	@Override
	public List<? extends ExpressionType<S>> getComponents() {
		return (List<? extends ExpressionType<S>>) super.getComponents();
	}
}
