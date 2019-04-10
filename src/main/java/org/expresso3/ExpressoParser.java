package org.expresso3;

import java.io.IOException;

import org.expresso.stream.BranchableStream;

/**
 * A snapshot of a state in parsing
 *
 * @param <S> The type of the stream being parsed
 */
public interface ExpressoParser<S extends BranchableStream<?, ?>> {
	/** @return The stream being parsed */
	S getStream();

	int getQualityLevel();

	/**
	 * @param spaces The number of spaces to advance
	 * @return A parser for the stream <code>spaces</code> spaces ahead
	 * @throws IOException If an error occurs reading the stream
	 */
	ExpressoParser<S> advance(int spaces) throws IOException;

	/**
	 * @param expressionIds The expression type IDs to exclude
	 * @return A parser for the same place in the stream that will not parse expressions with the given types
	 */
	ExpressoParser<S> exclude(int... expressionIds);

	/**
	 * @param type The expression type to substitute cache for
	 * @param result The possibility to return for the type
	 * @return A parser for the same place in the stream that will always return the given possibility when parsing the given expression
	 *         type
	 */
	ExpressoParser<S> useCache(ExpressionType<? super S> type, Expression<S> result, Runnable onHit);

	/**
	 * @param type The expression type to parse with
	 * @return The most likely possibility for parsing the stream at this parser's position with the given expression type
	 * @throws IOException If an error occurs reading the stream
	 */
	Expression<S> parseWith(ExpressionType<? super S> type) throws IOException;

	/**
	 * @param expression The expression to branch
	 * @return Another interpretation of the stream by the expresssion's type
	 * @throws IOException If an error occurs reading the stream
	 */
	Expression<S> nextMatch(Expression<S> expression) throws IOException;
}
