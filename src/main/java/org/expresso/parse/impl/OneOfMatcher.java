package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.*;

/**
 * Acts as a switch. Matches any one of a set of matchers against a stream.
 *
 * @param <S> The type of stream to parse
 */
public class OneOfMatcher<S extends BranchableStream<?, ?>> extends ComposedMatcher<S> {
	/** @see ComposedMatcher#ComposedMatcher(String, Set) */
	protected OneOfMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public String getTypeName() {
		return "one-of";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		Set<String> ret = new java.util.LinkedHashSet<>();
		for(ParseMatcher<?> sub : getComposed())
			ret.addAll(sub.getPotentialBeginningTypeReferences(parser, session));
		return ret;
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		ParseMatch<SS> match = null;
		for(ParseMatcher<? super S> element : getComposed()) {
			SS streamCopy = (SS) stream.branch();
			ParseMatch<SS> optionMatch = parser.parseWith(streamCopy, session, element);
			if(optionMatch == null)
				continue;
			else if(optionMatch.isComplete() && optionMatch.getError() == null) {
				match = optionMatch;
				break;
			} else if(optionMatch.isBetter(match)) {
				// if(match != null)
				// theDebugger.matchDiscarded(match);
				match = optionMatch;
			}
		}
		if(match == null)
			return null;
		SS streamCopy = (SS) stream.branch();
		stream.advance(match.getLength());
		return new ParseMatch<>(this, streamCopy, match.getLength(), Arrays.asList(match), null, true);
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @param name The name for the matcher
	 * @return A builder to create a new one-of matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> buildOneOf(String name) {
		return new Builder<>(name);
	}

	/** @param <S> The type of stream to accommodate */
	public static class Builder<S extends BranchableStream<?, ?>> extends ComposedMatcher.Builder<S, OneOfMatcher<S>> {
		private Set<String> theTypes;

		/** @param name The name for the matcher */
		protected Builder(String name) {
			super(name);
			theTypes = new java.util.LinkedHashSet<>();
		}

		/**
		 * @param types The types to include from the matcher's children
		 * @return This builder, for chaining
		 */
		public Builder<S> include(String... types) {
			for(String type : types)
				theTypes.add(type);
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
		protected OneOfMatcher<S> create(String name, Set<String> tags) {
			return new OneOfMatcher<>(name, tags);
		}
	}
}
