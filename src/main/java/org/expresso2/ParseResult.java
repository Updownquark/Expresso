package org.expresso2;

public class ParseResult {
	public final Expression expression;
	public boolean streamComplete;

	public ParseResult(Expression expression, boolean success, boolean streamComplete) {
		this.expression = expression;
		this.streamComplete = streamComplete;
	}
}
