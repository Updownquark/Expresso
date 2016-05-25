package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;
import org.qommons.ex.ExIterable;

/**
 * Matches a sequence of other matchers
 *
 * @param <S> The type of stream this matcher can parse
 */
public class SequenceMatcher<S extends BranchableStream<?, ?>> extends ComposedMatcher<S> {
	/** @see ComposedMatcher#ComposedMatcher(String, Set) */
	protected SequenceMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public String getTypeName() {
		return "sequence";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		List<ParseMatcher<? super S>> composed = getComposed();
		return parser.parseMatchPaths(stream, session, (strm, sess, depth) -> parser.parseWith(strm, sess, composed.get(depth)),
				composed.size(), composed.size(), this, depth -> "Expected " + composed.get(depth));
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @param name The name for the new matcher
	 * @return A builder to create a new sequence matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S, SequenceMatcher<S>> buildSequence(String name) {
		return new Builder<>(name);
	}

	/**
	 * Builds {@link SequenceMatcher}s
	 *
	 * @param <S> The type of stream to accommodate
	 * @param <M> The sub-type of sequence matcher to build
	 */
	public static class Builder<S extends BranchableStream<?, ?>, M extends SequenceMatcher<S>> extends ComposedMatcher.Builder<S, M> {
		/** @param name The name for the matcher */
		protected Builder(String name) {
			super(name);
		}

		@Override
		public Builder<S, M> tag(String... tags) {
			return (Builder<S, M>) super.tag(tags);
		}

		@Override
		public Builder<S, M> addChild(ParseMatcher<? super S> child) {
			return (Builder<S, M>) super.addChild(child);
		}

		@Override
		protected M create(String name, Set<String> tags) {
			return (M) new SequenceMatcher<>(name, tags);
		}
	}
}
