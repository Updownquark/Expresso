package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;
import org.qommons.ex.ExIterable;

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
			return Collections.emptyMap();
		else if(theMinRepeat == 0 && theMaxRepeat == Integer.MAX_VALUE)
			return Collections.emptyMap();
		java.util.LinkedHashMap<String, String> ret = new java.util.LinkedHashMap<>();
		if(theMinRepeat > 0)
			ret.put("min", "" + theMinRepeat);
		if(theMaxRepeat < Integer.MAX_VALUE)
			ret.put("max", "" + theMaxRepeat);
		return ret;
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		return parser.parseMatchPaths(stream, session, (strm, sess, depth) -> super.match(strm, parser, sess), theMinRepeat,
				theMaxRepeat < 0 ? Integer.MAX_VALUE : theMaxRepeat, this,
						depth -> "At least " + theMinRepeat + " repetition" + (theMinRepeat > 1 ? "s" : "") + " expected");
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
