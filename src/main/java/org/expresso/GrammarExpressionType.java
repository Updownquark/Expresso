package org.expresso;

import org.expresso.stream.BranchableStream;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;

/**
 * A top-level expression type (either a {@link ConfiguredExpressionType} or a {@link ExpressionClass}) configured in a
 * {@link ExpressoGrammar grammar}
 *
 * @param <S> The stream super-type of the expression
 */
public interface GrammarExpressionType<S extends BranchableStream<?, ?>> extends ExpressionType<S>, Comparable<GrammarExpressionType<?>> {
	/** @return The grammar that this expression type belongs to */
	ExpressoGrammar<S> getGrammar();

	/** @return The expression type's configured name */
	String getName();

	@Override
	default boolean isCacheable() {
		return true;
	}

	@Override
	default int compareTo(GrammarExpressionType<?> o) {
		return QommonsUtils.compareNumberTolerant(getName(), o.getName(), true, true);
	}

	/** @return Classes of expressions that may be a part of this expression's content without affecting its syntax */
	BetterList<? extends ExpressionClass<S>> getIgnorables();
}
