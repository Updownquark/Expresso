package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.*;

import org.expresso.parse.*;
import org.qommons.ex.ExIterable;
import org.qommons.ex.ExIterator;

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

	/** @return The matcher that this matcher searches for */
	public ParseMatcher<? super S> getMatcher() {
		return theMatcher;
	}

	@Override
	public List<ParseMatcher<? super S>> getComposed() {
		return java.util.Collections.unmodifiableList(Arrays.asList(theMatcher));
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		return () -> new ExIterator<ParseMatch<SS>, IOException>() {
			private SS copy = (SS) stream.clone();
			private boolean found;

			@Override
			public boolean hasNext() throws IOException {
				return !found && !(copy.isDiscovered() && copy.getDiscoveredLength() == 0);
			}

			@Override
			public ParseMatch<SS> next() throws IOException {
				if (!hasNext())
					throw new java.util.NoSuchElementException();
				ExIterator<ParseMatch<SS>, IOException> contentIter = parser.parseWith(copy, session, theMatcher).iterator();
				if (!contentIter.hasNext()) {
					copy = (SS) copy.advance(1);
					return null;
				}
				ParseMatch<SS> contentNext = contentIter.next();
				if (contentNext == null || !contentNext.isComplete() || contentNext.getError() != null) {
					copy = (SS) copy.advance(1);
					return null;
				}
				found = true;
				return new ParseMatch<>(UpToMatcher.this, stream, copy.getPosition() - stream.getPosition(), Collections.EMPTY_LIST, null,
						true);
			}
		};
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		ret.append("\n\t").append(theMatcher.toString().replaceAll("\n", "\n\t"));
		return ret.toString();
	}
}
