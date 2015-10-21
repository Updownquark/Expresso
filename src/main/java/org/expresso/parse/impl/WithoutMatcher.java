package org.expresso.parse.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.expresso.parse.*;

/**
 * Causes specified matcher types to be excluded from parsing
 *
 * @param <S> The type of stream to parse
 */
public class WithoutMatcher<S extends BranchableStream<?, ?>> extends SequenceMatcher<S> {
	private final Set<String> theExcludedTypes;

	/** @param types The types to exclude */
	public WithoutMatcher(String... types) {
		super(null, Collections.EMPTY_SET);
		theExcludedTypes = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(types)));
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		ExcludedTypesParser<? super SS> filteredParser = new ExcludedTypesParser<>(parser);
		filteredParser.excludeTypes(theExcludedTypes.toArray(new String[theExcludedTypes.size()]));
		return super.match(stream, filteredParser, session);
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @return A builder to create a new without matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> buildWithout() {
		return new Builder<>();
	}

	/**
	 * A parser that wraps another parser and parses everything identically, except that it does not attempt to parse with certain matchers
	 *
	 * @param <S> The type of stream this parser can parse
	 */
	public static class ExcludedTypesParser<S extends BranchableStream<?, ?>> extends BaseMatcher<S> implements ExpressoParser<S> {
		private final ExpressoParser<S> theWrapped;

		private final Set<String> theExcludedTypes;

		/** @param wrap The parser to wrap */
		public ExcludedTypesParser(ExpressoParser<S> wrap) {
			super(wrap.getName(), wrap.getTags());
			theWrapped = wrap;
			theExcludedTypes = new HashSet<>();
		}

		/** @return The wrapped parser */
		public ExpressoParser<S> getWrapped() {
			return theWrapped;
		}

		/** @param types The types to exclude from parsing */
		public void excludeTypes(String... types) {
			for(String type : types)
				theExcludedTypes.add(type);
		}

		/** @param types The types to re-include in parsing */
		public void includeTypes(String... types) {
			for(String type : types)
				theExcludedTypes.remove(type);
		}

		/** @return The (modifiable) set of types that are excluded from parsing by this parser */
		public Set<String> getExcludedTypes() {
			return theExcludedTypes;
		}

		@Override
		public ParseSession createSession() {
			return theWrapped.createSession();
		}

		@Override
		public Collection<ParseMatcher<? super S>> getMatchersFor(String... types) {
			Collection<String> filteredTypes = new ArrayList<>(Arrays.asList(types));
			filteredTypes.removeAll(theExcludedTypes);
			Collection<ParseMatcher<? super S>> wrapParsers = theWrapped
				.getMatchersFor(filteredTypes.toArray(new String[filteredTypes.size()]));
			return wrapParsers.stream().filter(matcher -> {
				if(theExcludedTypes.contains(matcher.getName()))
					return false;
				for(String tag : matcher.getTags())
					if(theExcludedTypes.contains(tag))
						return false;
				return true;
			}).collect(Collectors.toList());
		}

		@Override
		public <SS extends S> ParseMatch<SS> parse(SS stream, ParseSession session,
			Collection<? extends ParseMatcher<? super SS>> matchers) {
			return theWrapped.parse(stream, session, matchers);
		}
	}

	/** @param <S> The type of stream to accommodate */
	public static class Builder<S extends BranchableStream<?, ?>> extends SequenceMatcher.Builder<S, WithoutMatcher<S>> {
		private Set<String> theTypes;

		/** Creates the builder */
		protected Builder() {
			super(null);
			theTypes = new java.util.LinkedHashSet<>();
		}

		/**
		 * @param types The types to exclude from the matcher's children
		 * @return This builder, for chaining
		 */
		public Builder<S> exclude(String... types) {
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
		protected WithoutMatcher<S> create(String name, Set<String> tags) {
			return new WithoutMatcher<>(theTypes.toArray(new String[theTypes.size()]));
		}
	}
}
