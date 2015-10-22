package org.expresso.parse.impl;

import java.util.*;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatcher;

/**
 * A matcher that is composed of any number of other matchers
 *
 * @param <S> The type of stream that this matcher can parse
 */
public abstract class ComposedMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	private List<ParseMatcher<? super S>> theComposed;

	/**
	 * @param name The name for this matcher
	 * @param tags The tags that may be used to reference this matcher in a parser
	 */
	protected ComposedMatcher(String name, Set<String> tags) {
		super(name, tags);
		theComposed = new ArrayList<>();
	}

	/** @param composed An matcher element to add to this composed matcher */
	protected void addComposed(ParseMatcher<? super S> composed) {
		theComposed.add(composed);
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		LinkedHashSet<String> depends = new LinkedHashSet<>();
		for(ParseMatcher<? super S> element : theComposed)
			depends.addAll(element.getExternalTypeDependencies());
		return depends;
	}

	/** @return The composed matchers making up this matcher */
	public List<ParseMatcher<? super S>> getComposed() {
		return java.util.Collections.unmodifiableList(theComposed);
	}

	/** @param child The child for this composite matcher */
	protected void addChild(ParseMatcher<? super S> child) {
		theComposed.add(child);
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		for(ParseMatcher<? super S> composed : theComposed)
			ret.append("\n\t").append(composed.toString().replaceAll("\n", "\n\t"));
		return ret.toString();
	}

	/**
	 * Builds a composed matcher
	 *
	 * @param <S> The type of stream that the matcher will use
	 * @param <M> The sub-type of composed matcher to build
	 */
	protected static abstract class Builder<S extends BranchableStream<?, ?>, M extends ComposedMatcher<S>> {
		private final String theName;
		private Set<String> theTags;
		private List<ParseMatcher<? super S>> theChildren;

		/** @param name The name for the matcher */
		protected Builder(String name) {
			theName = name;
			theTags = new java.util.LinkedHashSet<>();
			theChildren = new ArrayList<>();
		}

		/**
		 * @param tags The tags to apply to the matcher
		 * @return This builder, for chaining
		 */
		public Builder<S, M> tag(String... tags) {
			for(String tag : tags)
				theTags.add(tag);
			return this;
		}

		/**
		 * @param child The next child for the composite matcher
		 * @return This builder, for chaining
		 */
		public Builder<S, M> addChild(ParseMatcher<? super S> child) {
			theChildren.add(child);
			return this;
		}

		/**
		 * Creates a new matcher
		 *
		 * @param name The name for the matcher
		 * @param tags The tags for the matcher
		 * @return The new matcher to be configured
		 */
		protected abstract M create(String name, Set<String> tags);

		/**
		 * Configures a new matcher before returning it to be used
		 *
		 * @param matcher The matcher to configure
		 */
		protected void configure(M matcher) {
			for(ParseMatcher<? super S> child : theChildren)
				matcher.addChild(child);
		}

		/**
		 * Builds the matcher according to this builder's configuration
		 *
		 * @return The new matcher
		 */
		public M build() {
			M ret = create(theName, Collections.unmodifiableSet(theTags));
			configure(ret);
			return ret;
		}
	}
}
