package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibility<S extends BranchableStream<?, ?>> {
	int length();

	int advanceInStream() throws IOException;

	int getErrorCount();

	boolean isComplete();

	Expression<S> getExpression();
}
