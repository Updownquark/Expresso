package org.expresso;

import org.expresso.stream.BranchableStream;

/**
 * A simple expression base type
 *
 * @param <S> The type of stream the expression was parsed from
 */
public abstract class AbstractExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final S theStream;
	private final ExpressionType<? super S> theType;

	/**
	 * @param stream The stream the expression was parsed from
	 * @param type The type that parsed the expression
	 */
	public AbstractExpression(S stream, ExpressionType<? super S> type) {
		theStream = stream;
		theType = type;
	}

	@Override
	public S getStream() {
		return theStream;
	}

	@Override
	public ExpressionType<? super S> getType() {
		return theType;
	}

	@Override
	public String toString() {
		return theStream.printContent(0, length(), null).toString();
	}
}
