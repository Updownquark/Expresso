package org.expresso.parse;

import java.io.IOException;
import java.util.Set;

/**
 * Parses a defined set of content from a stream
 *
 * @param <S> The type of stream that may be parsed
 */
public interface ParseMatcher<S extends BranchableStream<?, ?>> {
	/** @return The name of this matcher */
	String getName();

	/** @return The set of tags that may be used to refer to this matcher in a parser */
	Set<String> getTags();

	/** @return The set of type/tag names that this matcher refers to and must be supplied by the parser */
	Set<String> getExternalTypeDependencies();

	/**
	 * Parses this matcher's content from the beginning of stream, advancing the stream past the content
	 *
	 * @param <SS> The sub-type of stream to parse
	 * @param stream The stream to parse
	 * @param parser The parser to use to parse reference types
	 * @param session The current parse session
	 * @return The parsed match, or null if this matcher does not recognize the content at the beginning of the stream
	 * @throws IOException If an error occurs retrieving the data for matching
	 */
	<SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException;
}
