package org.expresso.parse.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.expresso.parse.*;
import org.expresso.parse.impl.WithoutMatcher.ExcludedTypesParser;

/**
 * Counters the effect of {@link WithoutMatcher}
 *
 * @param <S> The type of stream to parse
 */
public class WithMatcher<S extends BranchableStream<?, ?>> extends SequenceMatcher<S> {
	private final Set<String> theIncludedTypes;

	/** @param types The types to include */
	public WithMatcher(String... types) {
		super(null, Collections.EMPTY_SET);
		theIncludedTypes = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(types)));
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		if(!(parser instanceof ExcludedTypesParser))
			return super.match(stream, parser, session);
		else {
			ExcludedTypesParser<? super SS> filteredParser = (ExcludedTypesParser<? super SS>) parser;
			Set<String> toReExclude = new LinkedHashSet<>();
			for(String include : theIncludedTypes) {
				if(filteredParser.getExcludedTypes().remove(include))
					toReExclude.add(include);
				else
					System.out.println("WARNING: " + include + " not excluded from parsing");
			}
			ParseMatch<SS> ret = super.match(stream, parser, session);
			filteredParser.getExcludedTypes().addAll(toReExclude);
			return ret;
		}
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @return A builder to create a new with matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> buildWith() {
		return new Builder<>();
	}

	/** @param <S> The type of stream to accommodate */
	public static class Builder<S extends BranchableStream<?, ?>> extends SequenceMatcher.Builder<S, WithMatcher<S>> {
		private Set<String> theTypes;

		/** Creates the builder */
		protected Builder() {
			super(null);
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
		protected WithMatcher<S> create(String name, Set<String> tags) {
			return new WithMatcher<>(theTypes.toArray(new String[theTypes.size()]));
		}
	}
}
