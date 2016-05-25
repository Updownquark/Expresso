package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.*;
import org.qommons.ex.ExIterable;

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
	public String getTypeName() {
		return "white-spaced";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public Set<String> getTags() {
		return java.util.Collections.EMPTY_SET;
	}

	@Override
	public List<ParseMatcher<? super BranchableStream<?, ?>>> getComposed() {
		return java.util.Collections.EMPTY_LIST;
	}

	@Override
	public <SS extends BranchableStream<?, ?>> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		throw new IllegalStateException("Group parser does not actually parse.  It cannot be added to a parser");
	}

	@Override
	public String toShortString() {
		return getTypeName();
	}
}
