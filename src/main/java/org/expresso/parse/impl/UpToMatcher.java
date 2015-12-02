package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

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

	@Override
	public String getTypeName() {
		return "up-to";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return java.util.Collections.EMPTY_SET;
	}

	/** @return The matcher that this matcher searches for */
	public ParseMatcher<? super S> getMatcher() {
		return theMatcher;
	}

	@Override
	public List<ParseMatcher<? super S>> getComposed() {
		return java.util.Collections.unmodifiableList(Arrays.asList(theMatcher));
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		SS streamBegin = (SS) stream.branch();
		SS streamCopy = (SS) stream.branch();
		ParseMatch<SS> end = parser.parseWith(streamCopy, session, theMatcher);
		while(end == null && (!stream.isDiscovered() || stream.getDiscoveredLength() > 0)) {
			stream.advance(1);
			streamCopy = (SS) stream.branch();
			end = parser.parseWith(streamCopy, session, theMatcher);
		}
		return new ParseMatch<>(this, streamBegin, stream.getPosition() - streamBegin.getPosition(), Collections.EMPTY_LIST, null, true);
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		ret.append("\n\t").append(theMatcher.toString().replaceAll("\n", "\n\t"));
		return ret.toString();
	}
}
