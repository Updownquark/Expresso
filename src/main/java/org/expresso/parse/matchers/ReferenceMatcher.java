package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;
import org.qommons.ex.ExFunction;
import org.qommons.ex.ExIterable;

/**
 * A matcher that just delegates to the outer parser
 *
 * @param <S> The type of stream to parse
 */
public class ReferenceMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	private final Set<String> theIncludedTypes;
	private final String [] theTypes;

	private final Set<String> theExcludedTypes;

	/**
	 * @param name The name for this parser
	 * @param tags The tags that may be used to reference this matcher in a parser
	 * @param includedTypes The names of the types that this matcher will user to parse parse, or empty to parse anything
	 * @param excludedTypes The names of the types that this matcher will not use to parse
	 */
	public ReferenceMatcher(String name, Set<String> tags, Set<String> includedTypes, Set<String> excludedTypes) {
		super(name, tags);
		if(includedTypes == null || includedTypes.isEmpty())
			theIncludedTypes = Collections.EMPTY_SET;
		else
			theIncludedTypes = Collections.unmodifiableSet(includedTypes);
		theTypes = includedTypes.toArray(new String[includedTypes.size()]);
		if(excludedTypes == null || excludedTypes.isEmpty())
			theExcludedTypes = Collections.EMPTY_SET;
		else
			theExcludedTypes = Collections.unmodifiableSet(excludedTypes);
	}

	@Override
	public String getTypeName() {
		return "ref";
	}

	@Override
	public Map<String, String> getAttributes() {
		if (theIncludedTypes.isEmpty() && theExcludedTypes.isEmpty())
			return Collections.EMPTY_MAP;
		LinkedHashMap<String, String> ret = new LinkedHashMap<>();
		String typeStr = theIncludedTypes.toString();
		typeStr = typeStr.substring(1, typeStr.length() - 1);
		for(String type : theExcludedTypes)
			typeStr += ",!" + type;
		if(typeStr.startsWith(","))
			typeStr = typeStr.substring(1);
		ret.put("type", typeStr);
		return ret;
	}

	@Override
	public Set<String> getExternalTypeDependencies() {
		return theIncludedTypes;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return getReference(parser, session).stream().map(m -> m.getName()).collect(Collectors.toSet());
	}

	@Override
	public List<ParseMatcher<? super S>> getComposed() {
		return Collections.EMPTY_LIST;
	}

	/**
	 * @param <SS> The sub-type of the stream being parsed
	 * @param parser The parser parsing the stream
	 * @param session The parsing session
	 * @return The matchers that may be used to parse matches of this type
	 */
	public <SS extends S> Collection<ParseMatcher<? super SS>> getReference(ExpressoParser<? super SS> parser, ParseSession session) {
		Collection<ParseMatcher<? super SS>> matchers = new ArrayList<>(parser.getMatchersFor(session, theTypes));
		ExpressoParser.removeTypes(matchers, theExcludedTypes);
		return matchers;
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		SS streamCopy = (SS) stream.branch();
		ExFunction<ParseMatch<SS>, ParseMatch<SS>, IOException> map = refMatch -> refMatch == null ? null
				: new ParseMatch<>(this, streamCopy, refMatch.getLength(), Arrays.asList(refMatch), null, true);
		return parser.<SS> parseWith(stream, session, getReference(parser, session)).mapEx(map);
	}
}