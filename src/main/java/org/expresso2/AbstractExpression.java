package org.expresso2;

import org.expresso.parse.BranchableStream;

public abstract class AbstractExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final S theStream;
	private final ExpressionComponent<? super S> theType;

	public AbstractExpression(S stream, ExpressionComponent<? super S> type) {
		theStream = stream;
		theType = type;
	}

	@Override
	public S getStream() {
		return theStream;
	}

	@Override
	public ExpressionComponent<? super S> getType() {
		return theType;
	}

	@Override
	public String toString() {
		return theStream.printContent(0, length(), null).toString();
	}
}
