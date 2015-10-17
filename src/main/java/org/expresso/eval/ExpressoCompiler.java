package org.expresso.eval;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatch;

public interface ExpressoCompiler<S extends BranchableStream<?, ?>, E> extends ExpressoCompileMatcher<S, E> {
	<SS extends S> E compile(ParseMatch<SS> match);

	@Override
	default <SS extends S> E compile(ParseMatch<SS> match, ExpressoCompiler<? super SS, E> compiler) {
		return compile(match);
	}
}
