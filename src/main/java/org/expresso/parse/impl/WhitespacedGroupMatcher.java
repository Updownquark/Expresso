package org.expresso.parse.impl;

import java.util.Set;

import org.expresso.parse.*;

/** A placeholder for grouping white space with parsed matches */
public class WhitespacedGroupMatcher implements ParseMatcher<BranchableStream<?, ?>> {
	/** The instance to use */
	public static final WhitespacedGroupMatcher MATCHER = new WhitespacedGroupMatcher();

	private WhitespacedGroupMatcher() {
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public Set<String> getTags() {
		return java.util.Collections.EMPTY_SET;
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		return java.util.Collections.EMPTY_SET;
	}

	@Override
	public <SS extends BranchableStream<?, ?>> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		throw new IllegalStateException("Group parser does not actually parse.  It cannot be added to a parser");
	}
}
