package org.expresso2;

import java.util.List;

import org.expresso.parse.BranchableStream;
import org.qommons.QommonsUtils;

public class ExpressionClass<S extends BranchableStream<?, ?>> extends OneOfExpression<S> implements Comparable<ExpressionClass<?>> {
	private final String theName;

	public ExpressionClass(int id, String name, List<? extends ExpressionType<S>> members) {
		super(id, members);
		theName = name;
	}

	public String getName() {
		return theName;
	}

	@Override
	public List<? extends ExpressionType<S>> getComponents() {
		return (List<? extends ExpressionType<S>>) super.getComponents();
	}

	@Override
	public int compareTo(ExpressionClass<?> o) {
		return QommonsUtils.compareNumberTolerant(theName, o.getName(), true, true);
	}
}
