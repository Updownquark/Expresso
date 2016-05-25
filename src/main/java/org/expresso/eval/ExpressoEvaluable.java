package org.expresso.eval;

import com.google.common.reflect.TypeToken;

public interface ExpressoEvaluable<T> {
	TypeToken<T> getType(EvaluationContext ctx);

	T eval(EvaluationContext ctx);
}
