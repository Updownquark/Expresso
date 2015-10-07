package org.expresso.parse.impl;

import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;

public class OneOfMatcher<S extends BranchableStream<?, ?>> extends ComposedMatcher<S> {
	protected OneOfMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser) {
		// TODO Auto-generated method stub
		return null;
	}
}
