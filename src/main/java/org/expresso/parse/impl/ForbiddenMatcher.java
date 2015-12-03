package org.expresso.parse.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

/**
 * Searches for forbidden content, returning an error if it is present
 *
 * @param <S> The type of stream to be parsed
 */
public class ForbiddenMatcher<S extends BranchableStream<?, ?>> extends BaseMatcher<S> {
	private final ParseMatcher<? super S> theContent;

	/**
	 * @param name The name for this matcher
	 * @param tags The tags that may be used to reference this matcher in a parser
	 * @param content The forbidden content to search for
	 */
	public ForbiddenMatcher(String name, Set<String> tags, ParseMatcher<? super S> content) {
		super(name, tags);
		theContent = content;
	}

	@Override
	public String getTypeName() {
		return "forbid";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		return theContent.getPotentialBeginningTypeReferences(parser, session);
	}

	@Override
	public List<ParseMatcher<? super S>> getComposed() {
		return java.util.Collections.unmodifiableList(Arrays.asList(theContent));
	}

	@Override
	public <SS extends S> List<ParseMatch<SS>> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session)
			throws IOException {
		SS streamCopy = (SS) stream.branch();
		List<ParseMatch<SS>> matches = parser.parseWith(stream, session, theContent);
		for (ParseMatch<SS> match : matches) {
			if (match.isComplete() && match.getError() == null)
				return Arrays.asList(new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(),
						Arrays.asList(match), "Forbidden content present", true));
		}
		return Collections.EMPTY_LIST;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		ret.append("\n\t").append(theContent.toString().replaceAll("\n", "\n\t"));
		return ret.toString();
	}
}
