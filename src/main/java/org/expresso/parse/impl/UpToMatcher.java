package org.expresso.parse.impl;

import java.util.Set;

import org.expresso.parse.*;

/**
 * Searches for another matcher and returns all content leading up to the matcher
 *
 * @param <S> The type of stream to search
 */
public abstract class UpToMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	private final ParseMatcher<? super S> theMatcher;

	/**
	 * @param name The name for this matcher
	 * @param tags The set of tags by which this matcher may be referred in a parser
	 * @param matcher The matcher for this matcher to search for
	 */
	public UpToMatcher(String name, Set<String> tags, ParseMatcher<? super S> matcher) {
		super(name, tags);
		theMatcher = matcher;
	}

	/** @return The matcher that this matcher searches for */
	public ParseMatcher<? super S> getMatcher() {
		return theMatcher;
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		SS streamCopy = (SS) stream.branch();
		while(theMatcher.parse(stream, parser, session) == null && (!stream.isDiscovered() || stream.getDiscoveredLength() > 0))
			stream.advance(1);
		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), java.util.Collections.EMPTY_LIST, null,
			true, false);
	}
}
