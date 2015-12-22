package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.expresso.parse.*;
import org.expresso.parse.ExpressoParser.SimpleMatchParser;
import org.qommons.ex.ExIterable;

/**
 * Parses raw text out of a file. Subclasses of this type are inspecting individual characters and not delegating any of their parsing to
 * other matchers.
 *
 * @param <S> The type of stream to parse
 */
public abstract class SimpleValueMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	/** @see BaseMatcher#BaseMatcher(String, Set) */
	protected SimpleValueMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public List<ParseMatcher<? super S>> getComposed() {
		return Collections.EMPTY_LIST;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return Collections.EMPTY_SET;
	}

	/** @return A string representation of the value this matcher is looking for */
	public abstract String getValueString();

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		ParseMatch<SS> PASS = new ParseMatch<>(this, stream, 0, Collections.EMPTY_LIST, null, true);
		return parser.parseMatchPaths(stream, session, new SimpleMatchParser<SS>() {
			@Override
			public ExIterable<ParseMatch<SS>, IOException> parse(SS strm, ParseSession sess, int depth) {
				return parser.parseByType(strm, sess, ExpressoParser.IGNORABLE);
			}
		}, 0, -1, this, null).map(m -> {
			if (m == null || !m.isComplete())
				return PASS;
			SS advanced = (SS) stream.advance(m.getLength());
			ParseMatch<SS> valueMatch = parseValue(advanced);
			if (valueMatch == null)
				return null;
			if (m.getLength() == 0)
				return valueMatch;
			return new ParseMatch<>(WhitespacedGroupMatcher.MATCHER, stream, m.getLength() + valueMatch.getLength(), m.getChildren(), null,
					true);
		}).filter(m -> m != PASS);
	}

	/**
	 * Does the actual text parsing. This method is not required to advance the stream.
	 *
	 * @param <SS> The sub-type of stream to parse
	 * @param stream The stream to parse
	 * @return The text match, or null if the match was not present at the beginning of the stream
	 * @throws IOException If an error occurs retrieving the data to parse
	 */
	protected abstract <SS extends S> ParseMatch<SS> parseValue(SS stream) throws IOException;
}
