package org.expresso.parse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.ex.ExIterable;

/**
 * Parses a defined set of content from a stream
 *
 * @param <S> The type of stream that may be parsed
 */
public interface ParseMatcher<S extends BranchableStream<?, ?>> {
	/** @return The name of this matcher */
	String getName();

	/** @return A representation of this matcher's type */
	String getTypeName();

	/** @return Attributes that distinguish this matcher from others of its type */
	Map<String, String> getAttributes();

	/** @return The set of tags that may be used to refer to this matcher in a parser */
	Set<String> getTags();

	/** @return The set of type/tag names that this matcher refers to and must be supplied by the parser */
	default Set<String> getExternalTypeDependencies() {
		Set<String> ret = new java.util.LinkedHashSet<>();
		for(ParseMatcher<? super S> sub : getComposed())
			ret.addAll(sub.getExternalTypeDependencies());
		return java.util.Collections.unmodifiableSet(ret);
	}

	/**
	 * @param parser The parser to parse the stream
	 * @param session The parsing session
	 * @return The names of all matchers that may match the first deep component of this matcher
	 */
	Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session);

	/**
	 * @param types The types to check
	 * @return Whether this matcher's name or one of its tags is contained in the given set of type names
	 */
	default boolean matchesType(Set<String> types) {
		if(getName() != null && types.contains(getName()))
			return true;
		for(String tag : getTags())
			if(types.contains(tag))
				return true;
		return false;
	}

	/** @return The sub-matchers that this matcher uses to parse content */
	List<ParseMatcher<? super S>> getComposed();

	/**
	 * <p>
	 * Parses this matcher's content from the beginning of stream. The position of the stream should not be affected.
	 * </p>
	 * <p>
	 * The work to parse each possible match should be done in the {@link java.util.Iterator#next()} method of the iterator, not in this
	 * call itself. The iterator's next method may return null if a possible match didn't pan out at all.
	 * </p>
	 *
	 * @param <SS> The sub-type of stream to parse
	 * @param stream The stream to parse
	 * @param parser The parser to use to parse reference types
	 * @param session The current parse session
	 * @return All matches that may be intended by the input for this matcher. Will be empty if this matcher does not recognize the content
	 */
	<SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session);
}
