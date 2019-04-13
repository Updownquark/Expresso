package org.expresso.types;

import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;

/**
 * A simple abstract implementation of expression type that does nothing more than hold the cache ID
 *
 * @param <S> The type of the stream
 */
public abstract class AbstractExpressionType<S extends BranchableStream<?, ?>> implements ExpressionType<S> {
	private final int theCacheId;

	/** @param id The cache ID for this expression type */
	public AbstractExpressionType(int id) {
		this.theCacheId = id;
	}

	@Override
	public int getId() {
		return theCacheId;
	}
}
