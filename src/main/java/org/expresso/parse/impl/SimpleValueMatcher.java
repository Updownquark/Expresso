package org.expresso.parse.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.expresso.parse.*;

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
		return java.util.Collections.EMPTY_LIST;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return java.util.Collections.EMPTY_SET;
	}

	/** @return A string representation of the value this matcher is looking for */
	public abstract String getValueString();

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		SS streamBegin = (SS) stream.branch();
		SS streamCopy = (SS) stream.branch();
		List<ParseMatch<SS>> ignorables = new ArrayList<>();
		ParseMatch<SS> ignorable = parser.parseByType(streamCopy, session, ExpressoParser.IGNORABLE);
		while(ignorable != null) {
			ignorables.add(ignorable);
			ignorable = parser.parseByType(streamCopy, session, ExpressoParser.IGNORABLE);
		}
		int groupLength = streamCopy.getPosition() - stream.getPosition();
		ParseMatch<SS> valueMatch = parseValue(streamCopy);
		if(valueMatch == null)
			return null;
		groupLength += valueMatch.getLength();
		stream.advance(groupLength);
		if(ignorables.isEmpty())
			return valueMatch;
		else {
			ignorables.add(valueMatch);
			return new ParseMatch<>(WhitespacedGroupMatcher.MATCHER, streamBegin, groupLength, ignorables, null, true);
		}
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
