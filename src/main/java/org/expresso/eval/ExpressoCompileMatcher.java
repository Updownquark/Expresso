package org.expresso.eval;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatch;

public interface ExpressoCompileMatcher<S extends BranchableStream<?, ?>, E> {
	<SS extends S> E compile(ParseMatch<SS> match, ExpressoCompiler<? super SS, E> compiler, EvaluationContext ctx);
}
