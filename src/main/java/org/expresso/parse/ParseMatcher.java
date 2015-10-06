package org.expresso.parse;

public interface ParseMatcher<S extends BranchableStream<?, ?, S>> {
	String getName();

	ParseMatch parse(S stream, ExpressoParser<? super S> parser);
}
