package org.expresso.parse;

public interface ParseMatcher<D> {
	String getName();

	ParseMatch parse(NavigableStream<D> stream, ExpressoParser<D> parser);
}
