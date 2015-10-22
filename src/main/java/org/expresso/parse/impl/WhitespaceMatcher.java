package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

/**
 * Searches for whitespace in a stream
 *
 * @param <S> The type of stream to search
 */
public class WhitespaceMatcher<S extends CharSequenceStream> implements ParseMatcher<S> {
	private final Set<String> theTags;

	/** @see BaseMatcher#BaseMatcher(String, Set) */
	public WhitespaceMatcher() {
		theTags = Collections
			.unmodifiableSet(java.util.Arrays.asList(ExpressoParser.IGNORABLE).stream().collect(java.util.stream.Collectors.toSet()));
	}

	@Override
	public String getName() {
		return ExpressoParser.WHITE_SPACE;
	}

	@Override
	public Set<String> getTags() {
		return theTags;
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		return Collections.EMPTY_SET;
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		if(!isNextWS(stream))
			return null;
		SS streamCopy = (SS) stream.branch();
		do {
			stream.advance(1);
		} while(isNextWS(stream));
		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), Collections.EMPTY_LIST, null, true);
	}

	private boolean isNextWS(CharSequenceStream stream) {
		if(stream.getDiscoveredLength() == 0 && stream.isDiscovered())
			return false;
		return isWhitespace(stream.charAt(0));
	}

	/**
	 * @param ch The character to check
	 * @return Whether the given character qualifies as white space
	 */
	protected boolean isWhitespace(char ch) {
		return Character.isWhitespace(ch);
	}

	@Override
	public String toString() {
		return "whitespace";
	}
}
