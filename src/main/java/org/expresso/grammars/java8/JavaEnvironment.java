package org.expresso.grammars.java8;

import org.expresso.runtime.typed.TypedExpressoEnvironment;

public class JavaEnvironment<E extends JavaEnvironment<E>> extends TypedExpressoEnvironment<E> {
	private boolean isAssertionEnabled;

	public JavaEnvironment(TypedExpressoEnvironment<E> parent) {
		super(parent);
	}

	public boolean isAssertionEnabled() {
		return isAssertionEnabled;
	}

	public E setAssertionEnabled(boolean assertionEnabled) {
		isAssertionEnabled = assertionEnabled;
		return (E) this;
	}
}
