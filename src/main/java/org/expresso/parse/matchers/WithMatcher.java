package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.*;

import org.expresso.parse.*;
import org.qommons.ex.ExIterable;

/**
 * Fully or partially counters the effect of {@link WithoutMatcher}
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
	public String getTypeName() {
		return "with";
	}

	@Override
	public Map<String, String> getAttributes() {
		LinkedHashMap<String, String> ret = new LinkedHashMap<>();
		String typeStr = theIncludedTypes.toString();
		typeStr = typeStr.substring(1, typeStr.length() - 1);
		ret.put("type", typeStr);
		return ret;
	}

	/** @return The matcher types that may be parsed as children of this matcher */
	public Set<String> getIncludedTypes() {
		return theIncludedTypes;
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		return doInSession(session, () -> super.match(stream, parser, session));
	}

	private interface ExceptionableAction<T, E extends Throwable> {
		T run() throws E;
	}

	private <T, E extends Throwable> T doInSession(ParseSession session, ExceptionableAction<T, E> run) throws E {
		Set<String> toReExclude = new java.util.LinkedHashSet<>();
		for(String type : theIncludedTypes) {
			if(session.includeType(type))
				toReExclude.add(type);
			else
				System.err.println("Type " + type + " was not excluded");
		}
		try {
			return run.run();
		} finally {
			for(String type : toReExclude)
				session.excludeType(type);
		}
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		StringBuilder typeStr = new StringBuilder(" type=\"");
		boolean first = true;
		for(String type : theIncludedTypes) {
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
