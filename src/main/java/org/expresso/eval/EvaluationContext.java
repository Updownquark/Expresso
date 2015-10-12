package org.expresso.eval;

import org.qommons.Transactable;

public interface EvaluationContext extends VariableSource, Transactable {
	boolean isCanceled();
}
