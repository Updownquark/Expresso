package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionComponent<S extends BranchableStream<?, ?>> {
	int getId();

	<S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException;
}
