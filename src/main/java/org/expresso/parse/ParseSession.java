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
}
