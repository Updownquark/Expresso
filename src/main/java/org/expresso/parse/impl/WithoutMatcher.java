package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		Set<String> toReInclude = new java.util.LinkedHashSet<>();
		for(String type : theExcludedTypes) {
			if(session.excludeType(type))
				toReInclude.add(type);
			else
				System.err.println("Type " + type + " was already excluded");
		}
		ParseMatch<SS> ret = super.match(stream, parser, session);
		for(String type : toReInclude)
			session.includeType(type);
		return ret;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		StringBuilder typeStr = new StringBuilder(" type=\"");
		boolean first = true;
		for(String type : theExcludedTypes) {
			if(!first)
				typeStr.append(',');
			first = false;
			typeStr.append(type);
		}
		typeStr.append('"');
		int newLine = ret.indexOf("\n");
		if(newLine < 0)
			ret.append(typeStr);
		else
			ret.insert(newLine, typeStr);
		return ret.toString();
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @return A builder to create a new without matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S> buildWithout() {
		return new Builder<>();
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
