package org.expresso.parse.impl;

import java.util.Collections;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatcher;

/**
 * A base matcher class
 *
 * @param <S> The type of stream that this matcher can accept
 */
public abstract class BaseMatcher<S extends BranchableStream<?, ?>> implements ParseMatcher<S> {
	private final String theName;
	private final Set<String> theTags;

	/**
	 * @param name The name for the matcher
	 * @param tags The tags that may refer to this matcher in a parser
	 */
	protected BaseMatcher(String name, Set<String> tags) {
		theName = name;
		theTags = Collections.unmodifiableSet(tags);
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Set<String> getTags() {
		return theTags;
	}
}
