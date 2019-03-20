package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionComponent<S extends BranchableStream<?, ?>> {
	int getCacheId();

	<S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser, boolean useCache) throws IOException;
}
