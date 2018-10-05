package org.expresso2;

import org.expresso.parse.BranchableStream;

public abstract class ExpressionComponent<S extends BranchableStream<?, ?>> {
	public final Integer id;

	public ExpressionComponent(int id) {
		this.id = id;
	}

	public abstract <S2 extends S> ExpressionPossibilitySequence<S2> tryParse(ExpressoParser<S2> session);
}
