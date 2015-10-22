package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseSession;
import org.qommons.ArrayUtils;

/**
 * A matcher that just delegates to the outer parser
 *
 * @param <S> The type of stream to parse
 */
public class ReferenceMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	private final Set<String> theTypeSet;
	private final String [] theTypes;

	/**
	 * @param name The name for this parser
	 * @param tags The tags that may be used to reference this matcher in a parser
	 * @param types The names of the types that this parser will parse, or zero-length to parse anything
	 */
	public ReferenceMatcher(String name, Set<String> tags, String... types) {
		super(name, tags);
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
	public Set<String> getExternalTypeDependencies() {
		return theTypeSet;
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) throws IOException {
		SS streamCopy = (SS) stream.branch();
		ParseMatch<SS> refMatch = parser.<SS> parseByType(stream, session, theTypes);
		if(refMatch == null)
			return null;
		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), Arrays.asList(refMatch), null, true);
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		if(!theTypeSet.isEmpty()) {
			ret.append(" type=\"");
			boolean first = true;
			for(String type : theTypes) {
				if(!first)
					ret.append(',');
				first = false;
				ret.append(type);
			}
			ret.append('"');
		}
		return ret.toString();
	}
}
