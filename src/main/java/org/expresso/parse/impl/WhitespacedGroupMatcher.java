package org.expresso.parse.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

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
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return java.util.Collections.EMPTY_SET;
	}

	@Override
	public List<ParseMatcher<? super BranchableStream<?, ?>>> getComposed() {
		return java.util.Collections.EMPTY_LIST;
	}

	@Override
	public <SS extends BranchableStream<?, ?>> List<ParseMatch<SS>> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		throw new IllegalStateException("Group parser does not actually parse.  It cannot be added to a parser");
	}
}
