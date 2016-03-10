package org.expresso.parse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The result of a {@link ParseMatcher} finding at least a partial {@link ParseMatcher#match(BranchableStream, ExpressoParser, ParseSession)
 * match} against some data
 *
 * @param <S> The type of data stream that was matched
 */
public class ParseMatch<S extends BranchableStream<?, ?>> implements Iterable<ParseMatch<S>> {
	private final ParseMatcher<? super S> theMatcher;

	private final S theStream;
	private final int theLength;

	private final List<ParseMatch<S>> theChildren;

	private final String theError;
	private final boolean isComplete;
	private final boolean isThisComplete;

	/**
	 * @param matcher The matcher that found the match
	 * @param stream The stream that the matched data was found in
	 * @param length The length of content in the stream that the matcher recognized
	 * @param children The result matches of the matcher's children
	 * @param error If the matcher recognized at least some content at the beginning of the stream, but could not correctly parse its whole
	 *            structure, then this will be a human-readable message describing what's wrong with the data, as far as the matcher can
	 *            tell. If the matcher had no problem understanding the data, this will be null.
	 * @param complete Whether the matcher was able to parse its complete structure. If this is false, then an error should also be
	 *            supplied. This may be true even if an error is supplied, though--matchers may report syntax errors in content that they
	 *            can still recognize.
	 */
	public ParseMatch(ParseMatcher<? super S> matcher, S stream, int length, List<ParseMatch<S>> children, String error, boolean complete) {
		theMatcher = matcher;
		theStream = (S) stream.branch();
		theStream.seal();
		theLength = length;
		theChildren = java.util.Collections.unmodifiableList(children);
		theError = error;
		isThisComplete = complete;
		int childLen = 0;
		for (ParseMatch<S> child : children) {
			if (child == null)
				throw new IllegalArgumentException("Null child");
			childLen += child.theLength;
		}
		if (childLen > length)
			throw new IllegalArgumentException("Children are longer than this match");
		isComplete = getIncompleteMatch() == null;
		if (!isThisComplete && getErrorMatch() == null)
			throw new IllegalArgumentException("Incomplete matches must have an error message");
	}

	/** @return The matcher that found this match */
	public ParseMatcher<?> getMatcher() {
		return theMatcher;
	}

	/** @return The data stream that this match was found in */
	public S getStream() {
		return theStream;
	}

	/** @return The lengt of the content in the stream that the matcher recognized */
	public int getLength() {
		return theLength;
	}

	/** @return The result matches of the matcher's children */
	public List<ParseMatch<S>> getChildren() {
		return theChildren;
	}

	/**
	 * @return If the matcher recognized at least some content at the beginning of the stream, but could not correctly parse its whole
	 *         structure, then this will be a human-readable message describing what's wrong with the data, as far as the matcher can tell.
	 *         If the matcher had no problem understanding the data, this will be null.
	 */
	public String getError() {
		ParseMatch<S> errorMatch = getErrorMatch();
		return errorMatch == null ? null : errorMatch.theError;
	}

	/**
	 * @return Whether the matcher was able to parse its complete structure. If this is false, then an error will also be present. This may
	 *         be true even if an error is supplied, though--matchers may report syntax errors in content that they can still recognize.
	 */
	public boolean isComplete() {
		return isComplete;
	}

	/** @return Whether this match is the direct source of its error (as opposed to one of its children) */
	public boolean isThisError() {
		return theError != null;
	}

	/** @return Whether this match represents whitespace in the stream */
	public boolean isWhitespace() {
		return theMatcher instanceof org.expresso.parse.matchers.WhitespaceMatcher;
	}

	/** @return The match that directly caused this match's error */
	public ParseMatch<S> getErrorMatch() {
		return search(match -> match.isThisError(), false);
	}

	/** @return The match in this structure that causes its incompleteness */
	public ParseMatch<S> getIncompleteMatch() {
		if(isComplete)
			return null;
		return search(match -> !match.isThisComplete, false);
	}

	/**
	 * @param name The name of the member to get
	 * @return The first {@link #localMatches() local} match in this structure (depth-first) whose matcher has the given name
	 */
	public ParseMatch<S> getMember(String name) {
		return search(match -> name.equals(match.getMatcher().getName()), true);
	}

	/**
	 * @param name The name of the members to get
	 * @return All {@link #localMatches() local} matches in this structure (depth-first order) whose matchers have the given name
	 */
	public List<ParseMatch<S>> getMembers(String name) {
		return searchAll(match -> name.equals(match.getMatcher().getName()), true);
	}

	/**
	 * @param tag The tag to search for
	 * @return The first {@link #localMatches() local} match in this structure (depth-first) whose matcher is tagged with the given tag
	 */
	public ParseMatch<S> getTag(String tag) {
		return search(match -> match.getMatcher().getTags().contains(tag), true);
	}

	/**
	 * @param tag The tag to search for
	 * @return All {@link #localMatches() local} matches in this structure (depth-first order) whose matchers are tagged with the given tag
	 */
	public List<ParseMatch<S>> getTags(String tag) {
		return searchAll(match -> match.getMatcher().getTags().contains(tag), true);
	}

	/**
	 * Searches breadth-first for the first match passing the given test
	 *
	 * @param test The test to search with
	 * @param local Whether to limit the search to matches matched by this match's configuration
	 * @return The match found to pass the test, or null if no such match was found
	 */
	public ParseMatch<S> search(Predicate<? super ParseMatch<S>> test, boolean local) {
		for(ParseMatch<S> match : (local ? localMatches() : this))
			if(test.test(match))
				return match;
		return null;
	}

	/**
	 * Searches breadth-first for all matches passing the given test
	 *
	 * @param test The test to search with
	 * @param local Whether to limit the search to matches matched by this match's configuration
	 * @return The matches found to pass the test
	 */
	public List<ParseMatch<S>> searchAll(Predicate<? super ParseMatch<S>> test, boolean local) {
		List<ParseMatch<S>> ret = new ArrayList<>();
		for(ParseMatch<S> match : (local ? localMatches() : this))
			if(test.test(match))
				ret.add(match);
		return java.util.Collections.unmodifiableList(ret);
	}

	/** Implements a depth-first iteration over this match's structure. The first match returned will be {@code this}. */
	@Override
	public java.util.Iterator<ParseMatch<S>> iterator() {
		return org.qommons.IterableUtils.depthFirst(this, ParseMatch::getChildren, null).iterator();
	}

	/** @return A depth-first iterator over this match's local nodes, i.e. the nodes that this match's matcher parsed directly */
	public Iterable<ParseMatch<S>> localMatches() {
		return org.qommons.IterableUtils.depthFirst(this, ParseMatch::getChildren,
				match -> !(match.getMatcher() instanceof org.expresso.parse.matchers.ReferenceMatcher));
	}

	/**
	 * @param match The match to compare against
	 * @return Whether this match is a better match to the same section of text than the given match
	 */
	public boolean isBetter(ParseMatch<?> match) {
		if(match == null)
			return true;
		int len1 = nonErrorLength();
		int len2 = match.nonErrorLength();
		if(len1 == len2) {
			if(isComplete() && !match.isComplete())
				return true;
			if(!isComplete() && match.isComplete())
				return false;
			if(getError() == null && match.getError() != null)
				return true;
		}
		return len1 > len2;
	}

	/** @return The amount of non-trivial content in this match up to the first error or incompleteness */
	public int nonErrorLength() {
		if(isWhitespace() || theError != null)
			return 0;
		else if(getError() == null && isComplete)
			return theLength;
		else if(theChildren.isEmpty())
			return 0;
		else {
			int ret = 0;
			for(ParseMatch<?> sub : theChildren) {
				int nel = sub.nonErrorLength();
				ret += nel;
				if(nel < sub.theLength)
					break;
			}
			return ret;
		}
	}

	/** @return A string representation of the stream data that this match was parsed from */
	public String flatText() {
		StringBuilder ret = new StringBuilder();
		try {
			for(int i = 0; i < theLength; i++)
				ret.append(theStream.get(i));
		} catch(IOException e) {
			throw new IllegalStateException("Could not re-retrieve data to print the match", e);
		}
		return ret.toString();
	}

	@Override
	public String toString() {
		if(theChildren.isEmpty()) {
			return flatText();
		} else {
			StringBuilder ret = new StringBuilder();
			if(theChildren.size() > 1)
				ret.append('(');
			boolean first = true;
			for(ParseMatch<S> match : theChildren) {
				if(!first)
					ret.append(",");
				first = false;
				ret.append(match);
			}
			if(theChildren.size() > 1)
				ret.append(')');
			return ret.toString();
		}
	}
}
