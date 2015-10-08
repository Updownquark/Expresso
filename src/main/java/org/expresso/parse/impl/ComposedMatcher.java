package org.expresso.parse.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
}
