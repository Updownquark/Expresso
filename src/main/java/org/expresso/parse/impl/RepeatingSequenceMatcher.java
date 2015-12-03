package org.expresso.parse.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

/**
 * Matches a sequence of other matchers optionally or multiple times
 *
 * @param <S> The type of stream this matcher can parse
 */
public class RepeatingSequenceMatcher<S extends BranchableStream<?, ?>> extends SequenceMatcher<S> {
	private final int theMinRepeat;
	private final int theMaxRepeat;

	/**
	 * @param name The name for this matcher
	 * @param tags The tags that this matcher can be referenced by
	 * @param minRepeat The minimum number of times that this matcher must match content in a stream sequentially to be valid
	 * @param maxRepeat The maximum number of times that this matcher will attempt to match its content in a stream sequentially
	 */
	protected RepeatingSequenceMatcher(String name, Set<String> tags, int minRepeat, int maxRepeat) {
		super(name, tags);
		theMinRepeat = minRepeat;
		theMaxRepeat = maxRepeat;
	}

	@Override
	public String getTypeName() {
		if(theMinRepeat == 0 && theMaxRepeat == 1)
			return "option";
		else
			return "repeat";
	}

	/** @return The minimum number of times that this matcher must match in a stream sequentially to be valid */
	public int getMin() {
		return theMinRepeat;
	}

	/** @return The maximum number of times that this matcher will attempt to match its content in a stream sequentially */
	public int getMax() {
		return theMaxRepeat;
	}

	@Override
	public Map<String, String> getAttributes() {
		if(theMinRepeat == 0 && theMaxRepeat == 1)
			return java.util.Collections.EMPTY_MAP;
		else if(theMinRepeat == 0 && theMaxRepeat == Integer.MAX_VALUE)
			return java.util.Collections.EMPTY_MAP;
		java.util.LinkedHashMap<String, String> ret = new java.util.LinkedHashMap<>();
		if(theMinRepeat > 0)
			ret.put("min", "" + theMinRepeat);
		if(theMaxRepeat < Integer.MAX_VALUE)
			ret.put("max", "" + theMaxRepeat);
		return ret;
	}

	@Override
	public <SS extends S> List<ParseMatch<SS>> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session)
			throws IOException {
		List<List<ParseMatch<SS>>> paths = new ArrayList<>();
		SS copy = (SS) stream.branch();
		for (ParseMatch<SS> possibility : super.match(copy, parser, session)) {
			List<ParseMatch<SS>> path = new ArrayList<>();
			path.add(possibility);
			paths.add(path);
		}
		int iterations;
		boolean hasWayForward = true;
		for (iterations = 0; (theMaxRepeat < 0 || iterations < theMaxRepeat) && hasWayForward; iterations++) {
			int todo = todo; // Refactor to use parseNext()
			ListIterator<List<ParseMatch<SS>>> pathIter = paths.listIterator();
			while (pathIter.hasNext()) {
				List<ParseMatch<SS>> path = pathIter.next();
				if (path.size() != iterations || !path.get(path.size() - 1).isComplete())
					continue;

				hasWayForward = true;
				copy = (SS) stream.branch();
				for (ParseMatch<SS> pathEl : path)
					copy.advance(pathEl.getLength());
				List<ParseMatch<SS>> newMatches = super.match(copy, parser, session);
				if (newMatches.isEmpty())
					pathIter.remove();
				else {
					// Re-use the path list for the first match
					path.add(newMatches.get(newMatches.size() - 1));
					for (int j = 0; j < newMatches.size() - 1; j++) {
						List<ParseMatch<SS>> newPath = new ArrayList<>();
						// The path now has the first new match in it, so we can't just do addAll
						for (int k = 0; k < path.size() - 1; k++)
							newPath.add(path.get(k));
						newPath.add(newMatches.get(j));
						pathIter.add(newPath);
					}
				}
			}
		}

		copy = (SS) stream.branch();
		List<ParseMatch<SS>> ret = new ArrayList<>(paths.size());
		for (int i = 0; i < ret.size(); i++) {
			List<ParseMatch<SS>> path = paths.get(i);
			String errorMsg;
			if (!path.isEmpty() && path.get(path.size() - 1).getError() != null)
				errorMsg = null; // Let the deeper message come up
			else if (path.size() < theMinRepeat)
				errorMsg = "At least " + theMinRepeat + " repetition" + (theMinRepeat > 1 ? "s" : "") + " expected";
			else
				errorMsg = null;
			int length = 0;
			for (ParseMatch<SS> el : path)
				length += el.getLength();
			ret.add(new ParseMatch<>(this, copy, length, path, errorMsg, path.size() >= theMinRepeat));
		}
		return ret;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		if(theMinRepeat > 0 || theMaxRepeat < Integer.MAX_VALUE) {
			StringBuilder countStr = new StringBuilder();
			if(theMinRepeat > 0)
				countStr.append(" min=\"").append(theMinRepeat).append('"');
			if(theMaxRepeat > 0)
				countStr.append(" max=\"").append(theMaxRepeat).append('"');
			int newLine = ret.indexOf("\n");
			if(newLine < 0)
				ret.append(countStr);
			else
				ret.insert(newLine, countStr);
		}
		return ret.toString();
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @param name The name for matcher
	 * @return A builder to build a repeating matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> buildRepeat(String name) {
		return new Builder<>(name);
	}

	/** @param <S> The type of stream to accommodate */
	public static class Builder<S extends BranchableStream<?, ?>> extends SequenceMatcher.Builder<S, RepeatingSequenceMatcher<S>> {
		private int theMinimum = 0;

		private int theMaximum = 1;

		private boolean isMaxSet;

		/** @param name The name for the matcher */
		protected Builder(String name) {
			super(name);
		}

		/**
		 * @param min The minimum number of times the matcher's sequence may repeat
		 * @return This builder, for chaining
		 */
		public Builder<S> min(int min) {
			theMinimum = min;
			return this;
		}

		/**
		 * @param max The maximum number of times the matcher's sequence may repeat
		 * @return This builder, for chaining
		 */
		public Builder<S> max(int max) {
			isMaxSet = true;
			theMaximum = max;
			return this;
		}

		@Override
		public Builder<S> tag(String... tags) {
			return (Builder<S>) super.tag(tags);
		}

		@Override
		public Builder<S> addChild(ParseMatcher<? super S> child) {
			return (Builder<S>) super.addChild(child);
		}

		@Override
		protected RepeatingSequenceMatcher<S> create(String name, Set<String> tags) {
			if(theMaximum < theMinimum) {
				if(isMaxSet)
					throw new IllegalArgumentException(
						"Min and max values for the repeater are invalid: " + theMinimum + " and " + theMaximum);
				theMaximum = Integer.MAX_VALUE; // If the minimum is set but no maximum, assume max is infinite
			}
			return new RepeatingSequenceMatcher<>(name, tags, theMinimum, theMaximum);
		}
	}
}
