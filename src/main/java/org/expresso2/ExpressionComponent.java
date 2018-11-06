package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public abstract class ExpressionComponent<S extends BranchableStream<?, ?>> {
	public final Integer id;

	public ExpressionComponent(int id) {
		this.id = id;
	}

	public abstract <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> parser) throws IOException;
}
