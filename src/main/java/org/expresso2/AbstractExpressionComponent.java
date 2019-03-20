package org.expresso2;

import org.expresso.parse.BranchableStream;

public abstract class AbstractExpressionComponent<S extends BranchableStream<?, ?>> implements ExpressionComponent<S> {
	public final Integer id;

	public AbstractExpressionComponent(int id) {
		this.id = id;
	}

	@Override
	public int getCacheId() {
		return id;
	}
}
