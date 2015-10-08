package org.expresso.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ParseMatch<S extends BranchableStream<?, ?>> implements Iterable<ParseMatch<S>> {
	private final ParseMatcher<? super S> theMatcher;

	private final S theStream;
	private final int theLength;

	private final List<ParseMatch<S>> theChildren;

	private final String theError;
	private final boolean isComplete;
	private final boolean isThisComplete;

	private final boolean isWhitespace;

	public ParseMatch(ParseMatcher<? super S> matcher, S stream, int length, List<ParseMatch<S>> children, String error,
		boolean complete,
		boolean whitespace) {
		theMatcher = matcher;
		theStream = stream;
		theLength = length;
		theChildren = java.util.Collections.unmodifiableList(children);
		theError = error;
		isThisComplete = complete;
		isComplete = getIncompleteMatch() != null;
		isWhitespace = whitespace;
	}

	public ParseMatcher<?> getMatcher() {
		return theMatcher;
	}

	public S getStream() {
		return theStream;
	}

	public int getLength() {
		return theLength;
	}

	public List<ParseMatch<S>> getChildren() {
		return theChildren;
	}

	public boolean isComplete() {
		return isComplete;
	}

	public boolean isThisError() {
		return theError != null;
	}

	public String getError() {
		ParseMatch<S> errorMatch = getErrorMatch();
		return errorMatch == null ? null : errorMatch.theError;
	}

	public boolean isWhitespace() {
		return isWhitespace;
	}

	public ParseMatch<S> getErrorMatch() {
		return search(match -> match.isThisError(), false);
	}

	public ParseMatch<S> getIncompleteMatch() {
		if(isComplete)
			return null;
		return search(match -> !match.isThisComplete, false);
	}

	public ParseMatch<S> getMember(String name) {
		return search(match -> name.equals(match.getMatcher().getName()), true);
	}

	public List<ParseMatch<S>> getMembers(String name) {
		return searchAll(match -> name.equals(match.getMatcher().getName()), true);
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
		return org.qommons.ArrayUtils.depthFirst(this, ParseMatch::getChildren, null).iterator();
	}

	public Iterable<ParseMatch<S>> localMatches() {
		return org.qommons.ArrayUtils.depthFirst(this, ParseMatch::getChildren,
			match -> !(match.getMatcher() instanceof org.expresso.parse.impl.ReferenceMatcher));
	}

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
		return len2 > len1;
	}

	public int nonErrorLength() {
		if(isWhitespace || theError != null)
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

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		if(theChildren.isEmpty()) {
			for(int i = 0; i < theLength; i++)
				ret.append(theStream.get(i));
		} else {
			ret.append('(');
			for(ParseMatch<S> match : theChildren)
				ret.append(match.toString());
			ret.append(')');
		}
		return ret.toString();
	}
}
