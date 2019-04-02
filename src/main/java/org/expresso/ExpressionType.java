package org.expresso;

import java.io.IOException;

import org.expresso.stream.BranchableStream;

/**
 * A component that understands how to parse a particular type of expression from a stream
 *
 * @param <S> The super type of stream that this component understands
 */
public interface ExpressionType<S extends BranchableStream<?, ?>> {
	/** @return An ID by which this type's results may be cached, or -1 if caching should not be used for this type */
	int getCacheId();

	/**
	 * @param parser The parser to parse the expression from
	 * @return The parsed expression, or null if this type could not understand the information in the parser's stream
	 * @throws IOException If an error occurs reading the stream
	 */
	<S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException;
}
