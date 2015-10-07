package org.expresso.parse;

import java.util.Set;

public interface ParseMatcher<S extends BranchableStream<?, ?>> {
	String getName();

	Set<String> getTags();

	Set<String> getExternalTypeDependencies();

	<SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser);
}
