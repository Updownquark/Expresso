package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressoParser<S extends BranchableStream<?, ?>> {
	S getStream();

	ExpressoParser<S> advance(int spaces) throws IOException;

	ExpressoParser<S> exclude(int... expressionIds);

	default ExpressionPossibility<S> parseWith(ExpressionComponent<? super S> component) throws IOException {
		return parseWith(component, true);
	}

	ExpressionPossibility<S> parseWith(ExpressionComponent<? super S> component, boolean useCache) throws IOException;
}
