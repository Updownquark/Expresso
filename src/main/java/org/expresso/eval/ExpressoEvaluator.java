package org.expresso.eval;

import com.google.common.reflect.TypeToken;

public interface ExpressoEvaluator<T> {
	TypeToken<T> getType();

	T eval(EvaluationContext ctx);
}
