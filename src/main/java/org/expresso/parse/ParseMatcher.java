package org.expresso.parse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.qommons.ex.ExIterable;
import org.qommons.ex.ExIterator;

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
	 * Returns an iterator that parses possible matches for this matcher from the beginning of a stream. All the work of parsing should be
	 * done inside the iterator, not in this method itself or in the {@link ExIterable#iterator()} method. {@link ExIterable#iterator()} may
	 * perform initialization, but may not inspect the stream or invoke the parser.
	 * </p>
	 * <p>
	 * The {@link ExIterator#hasNext()} method may inspect the stream, but should never invoke the parser. If it is not possible to be
	 * certain whether a next match exists without invoking the parser, {@link ExIterator#hasNext()} may return true and then
	 * {@link ExIterator#next()} may return null if the match doesn't exist.
	 * </p>
	 *
	 * @param <SS> The sub-type of stream to parse
	 * @param stream The stream to parse
	 * @param parser The parser to use to parse reference types
	 * @param session The current parse session
	 * @return All matches that may be intended by the input for this matcher. Will be empty if this matcher does not recognize the content
	 */
	<SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session);

	String toShortString();
}
