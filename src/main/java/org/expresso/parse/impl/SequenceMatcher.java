package org.expresso.parse.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.expresso.parse.*;

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
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		SS streamCopy = (SS) stream.branch();
		List<ParseMatch<SS>> components = new ArrayList<>();
		ParseMatcher<? super S> missingEl = null;
		for(ParseMatcher<? super S> element : getComposed()) {
			ParseMatch<SS> component = parser.parseWith(stream, session, element);
			if(component == null) {
				missingEl = element;
				break;
			}
			components.add(component);
			if(!component.isComplete())
				break;
		}
		if(components.isEmpty())
			return null;
		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), components,
			missingEl == null ? null : "Expected " + missingEl, missingEl == null);
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
