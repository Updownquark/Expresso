package org.expresso.parse.impl;

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
	/** @see BaseMatcher#BaseMatcher(String, Set) */
	public WhitespaceMatcher() {
	}

	@Override
	public String getName() {
		return ExpressoParser.WHITE_SPACE;
	}

	@Override
	public Set<String> getTags() {
		return Collections.EMPTY_SET;
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		return Collections.EMPTY_SET;
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		if(!isWhitespace(stream.charAt(0)))
			return null;
		SS streamCopy = (SS) stream.branch();
		StringBuilder ws = new StringBuilder();
		while(stream.getDiscoveredLength() > 0 || !stream.isDiscovered() && isWhitespace(stream.charAt(0))) {
			ws.append(stream.charAt(0));
			stream.advance(1);
		}
		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), Collections.EMPTY_LIST, null, true);
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
