package org.expresso;

import java.util.List;

import org.expresso.stream.BranchableStream;
import org.qommons.collect.BetterList;

public class ConcreteExpressionClass<S extends BranchableStream<?, ?>> extends ExpressionClass<S> {
	public ConcreteExpressionClass(ExpressoGrammar<S> grammar, int id, String name, List<ExpressionClass<S>> parentClasses,
		List<ExpressionClass<S>> childClasses, BetterList<? extends GrammarExpressionType<S>> members,
		BetterList<? extends ExpressionClass<S>> ignorables) {
		super(grammar, id, name, parentClasses, childClasses, members, ignorables);
	}
}
