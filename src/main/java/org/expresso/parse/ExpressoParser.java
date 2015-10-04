package org.expresso.parse;

public interface ExpressoParser<D> extends ParseMatcher<D> {
	Iterable<ParseMatch> matches(NavigableStream<D> stream);
}
