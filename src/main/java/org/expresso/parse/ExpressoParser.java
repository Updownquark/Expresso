package org.expresso.parse;

public interface ExpressoParser<S extends BranchableStream<?, ?, S>> extends ParseMatcher<S> {
	Iterable<ParseMatch> matches(S stream);
}
