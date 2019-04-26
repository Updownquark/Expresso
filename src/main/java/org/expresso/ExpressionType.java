package org.expresso;

import java.io.IOException;

import org.expresso.stream.BranchableStream;

/**
 * A component that understands how to parse a particular type of expression from a stream
 *
 * @param <S> The super type of stream that this component understands
 */
public interface ExpressionType<S extends BranchableStream<?, ?>> {
	// public static final ProgramTracker TRACKER = new ProgramTracker("Expresso Parsing");

	/** @return An ID by which this type's results may be cached, or -1 if caching should not be used for this type */
	int getId();

	/** @return Whether expressions of this type should be cached to avoid re-evaluation at the same position */
	default boolean isCacheable() {
		return false; // Most expressions should not be cached
	}

	/**
	 * @param minQuality The minimum quality needed
	 * @return The best-possible {@link Expression#getMatchQuality() quality} of a match for this type that is zero-length
	 */
	int getEmptyQuality(int minQuality);

	/** @return The component expression types that this type uses */
	Iterable<? extends ExpressionType<? super S>> getComponents();

	/**
	 * @param <S2> The type of the stream being parsed
	 * @param parser The parser to parse the expression from
	 * @return The parsed expression, or null if this type could not understand the information in the parser's stream
	 * @throws IOException If an error occurs reading the stream
	 */
	<S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser, Expression<S2> start, Expression<S2> end) throws IOException;
}
