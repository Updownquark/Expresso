package org.expresso.types;

import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;

public abstract class AbstractExpressionType<S extends BranchableStream<?, ?>> implements ExpressionType<S> {
	public final int id;

	public AbstractExpressionType(int id) {
		this.id = id;
	}

	@Override
	public int getCacheId() {
		return id;
	}
}
