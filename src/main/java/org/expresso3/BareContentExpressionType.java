package org.expresso3;

import java.util.Collections;

import org.expresso.stream.BranchableStream;

/**
 * A tagging type implemented by expression types that parse raw data from the stream
 *
 * @param <S> The super-type of stream the expression type can parse
 */
public interface BareContentExpressionType<S extends BranchableStream<?, ?>> extends ExpressionType<S> {
	@Override
	default Iterable<? extends ExpressionType<? super S>> getComponents() {
		return Collections.emptyList();
	}
}
