package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatch;

/**
 * Parses a literal value (i.e. a concrete sequence of data values) out of a stream
 *
 * @param <C> The type of sequence that this matcher matches against
 * @param <S> The type of stream to parse
 */
public abstract class LiteralMatcher<C, S extends BranchableStream<?, ? super C>> extends SimpleValueMatcher<S> {
	private final C theValue;

	/**
	 * @param name The name of this matcher
	 * @param tags The tags by which this matcher may be referenced in a parser
	 * @param value The value to parse
	 */
	public LiteralMatcher(String name, Set<String> tags, C value) {
		super(name, tags);
		theValue = value;
	}

	/** @return The value that this matcher looks for */
	public C getValue() {
		return theValue;
	}

	@Override
	protected <SS extends S> ParseMatch<SS> parseValue(SS stream) throws IOException {
		int length = getLength();
		if(stream.discoverTo(length) < length)
			return null;
		if(!startsWithValue(stream))
			return null;
		return new ParseMatch<>(this, stream, length, java.util.Collections.EMPTY_LIST, null, true);
	}

	/** @return The length of this matcher's value */
	protected abstract int getLength();

	/**
	 * @param stream The stream to check
	 * @return Whether the given stream starts with this matcher's value
	 */
	protected abstract boolean startsWithValue(S stream);

	@Override
	public String getValueString() {
		return theValue.toString();
	}
}
