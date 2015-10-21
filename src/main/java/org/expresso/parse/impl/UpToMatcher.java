package org.expresso.parse.impl;

import java.util.Arrays;
import java.util.Set;

import org.expresso.parse.*;

/**
 * Searches for another matcher and returns all content leading up to the matcher
 *
 * @param <S> The type of stream to search
 */
public class UpToMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
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
	public Set<String> getExternalTypeDependencies() {
		return theMatcher.getExternalTypeDependencies();
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		SS streamBegin = (SS) stream.branch();
		SS streamCopy = (SS) stream.branch();
		ParseMatch<SS> end = parser.parseWith(streamCopy, session, theMatcher);
		while(end == null && (!stream.isDiscovered() || stream.getDiscoveredLength() > 0)) {
			stream.advance(1);
			streamCopy = (SS) stream.branch();
			end = parser.parseWith(streamCopy, session, theMatcher);
		}
		stream.advance(end.getLength());
		return new ParseMatch<>(this, streamBegin, stream.getPosition() - streamCopy.getPosition(), Arrays.asList(end), null, true);
	}
}
