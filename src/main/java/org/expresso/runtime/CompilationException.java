package org.expresso.runtime;

import org.expresso.Expression;

public class CompilationException extends Exception {
	private final Expression theExpresion;

	public CompilationException(Expression expression, String message, Throwable cause) {
		super(message, cause);
		theExpresion = expression;
	}

	public CompilationException(Expression expression, String message) {
		super(message);
		theExpresion = expression;
	}

	public Expression getExpresion() {
		return theExpresion;
	}
}
