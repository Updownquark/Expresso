package org.expresso.eval;

import org.expresso.parse.ParseMatch;

public interface ExpressoCompiler {
	ExpressoEvaluator<?> compile(ParseMatch<?> match, EvaluationContext ctx);
}
