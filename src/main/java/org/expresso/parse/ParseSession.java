package org.expresso.parse;

/** Contains data used by the parser to optimize parsing a piece of data */
public interface ParseSession {
	/**
	 * @param type The matcher type (name or tag) to exclude from parsing temporarily
	 * @return Whether this call was effective, e.g. true if the type was not excluded before this call
	 */
	boolean excludeType(String type);

	/**
	 * @param type The matcher type (name or tag) to re-include in parsing
	 * @return Whether this call was effective, e.g. true if the type was excluded before this call
	 */
	boolean includeType(String type);

	/**
	 * @param type The matcher type (name only, no tags) to check the cache for
	 * @param stream The stream being parsed
	 * @return True if the parser would use only its cache for a call to
	 *         {@link ExpressoParser#parseWith(BranchableStream, ParseSession, ParseMatcher...)} with a matcher of the given type at the
	 *         given stream's position
	 */
	boolean isCached(String type, BranchableStream<?, ?> stream);
}
