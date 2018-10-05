package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressoParser<S extends BranchableStream<?, ?>> {
	S getStream();

	ExpressoParser<S> advance(int spaces) throws IOException;

	ExpressoParser<S> exclude(int... expressionIds);

	ExpressionPossibilitySequence<S> parseWith(ExpressionComponent<? super S> component);
}
