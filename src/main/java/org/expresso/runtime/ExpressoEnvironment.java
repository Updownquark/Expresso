package org.expresso.runtime;

import java.util.List;
import java.util.Map;

public interface ExpressoEnvironment<E extends ExpressoEnvironment<E>> {
	String getCurrentScopeLabel();

	boolean hasLabel(String label);

	Map<String, ? extends Variable<?>> getVariables();

	Map<String, ? extends List<? extends Method<E, ?>>> getMethods();

	E scope(String label);
}
