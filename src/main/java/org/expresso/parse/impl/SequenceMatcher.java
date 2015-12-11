package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.*;
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
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		Set<String> ret = new java.util.LinkedHashSet<>();
		for(ParseMatcher<?> sub : getComposed()) {
			ret.addAll(sub.getPotentialBeginningTypeReferences(parser, session));
			if(sub instanceof ForbiddenMatcher)
				continue;
			else if(sub instanceof RepeatingSequenceMatcher && ((RepeatingSequenceMatcher<?>) sub).getMin() == 0)
				continue;
			else
				break;
		}
		return ret;
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session)
			throws IOException {
		return parser.parseMatchPaths(stream, session, (strm, sess, depth) -> getComposed().get(depth).match(strm, parser, sess),
				getComposed().size(), getComposed().size(), this, depth -> "Expected " + getComposed().get(depth));
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
