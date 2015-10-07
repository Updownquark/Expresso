package org.expresso.parse.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.qommons.ArrayUtils;

/**
 * A matcher that just delegates to the outer parser
 *
 * @param <S> The type of stream to parse
 */
public class ReferenceMatcher<S extends BranchableStream<?, ?>> implements ParseMatcher<S> {
	private final String theName;
	private final Set<String> theTypeSet;
	private final String [] theTypes;

	/**
	 * @param name The name for this parser
	 * @param types The names of the types that this parser will parse, or zero-length to parse anything
	 */
	public ReferenceMatcher(String name, String... types) {
		theName = name;
		LinkedHashSet<String> set = new LinkedHashSet<>();
		boolean duplicate = false;
		for(String type : types)
			duplicate |= !set.add(type);
		if(duplicate)
			System.err.println("WARNING: Reference has multiple types: " + ArrayUtils.toString(types));
		theTypeSet = Collections.unmodifiableSet(set);
		theTypes = set.toArray(new String[set.size()]);
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Set<String> getTags() {
		return Collections.EMPTY_SET;
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		return theTypeSet;
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser) {
		SS streamCopy = (SS) stream.branch();
		int prePosition = stream.getPosition();
		ParseMatch<SS> refMatch = parser.<SS> parse(stream, theTypes);
		int postPosition = stream.getPosition();
		if(refMatch == null)
			return null;
		return new ParseMatch<>(this, streamCopy, postPosition - prePosition, Arrays.asList(refMatch), null, true);
	}
}
