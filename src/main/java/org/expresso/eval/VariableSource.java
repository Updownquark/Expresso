package org.expresso.eval;

import java.util.Collection;

public interface VariableSource {
	Collection<Variable<?>> getDeclaredVariables();
	Collection<InvocableVariable<?>> getDeclaredFunctions();

	Variable<?> getDeclaredVariable(String name);
}
