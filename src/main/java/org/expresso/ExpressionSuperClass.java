package org.expresso;

import java.util.List;

import org.expresso.stream.BranchableStream;
import org.qommons.collect.BetterList;

public class ExpressionSuperClass<S extends BranchableStream<?, ?>> extends ExpressionClass<S> {
	public ExpressionSuperClass(ExpressoGrammar<S> grammar, int id, String name, List<ExpressionClass<S>> parentClasses,
		BetterList<ExpressionClass<S>> childClasses, BetterList<? extends ExpressionClass<S>> ignorables) {
		super(grammar, id, name, parentClasses, childClasses, childClasses, ignorables);
	}

	@Override
	public BetterList<? extends ExpressionClass<S>> getComponents() {
		return (BetterList<? extends ExpressionClass<S>>) super.getComponents();
	}

	@Override
	public String toString() {
		return "Super" + super.toString();
	}
}
