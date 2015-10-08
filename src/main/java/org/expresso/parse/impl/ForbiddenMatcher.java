package org.expresso.parse.impl;

import java.util.Arrays;
import java.util.Set;

import org.expresso.parse.*;

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
	public Set<String> getExternalTypeDependencies() {
		return theContent.getExternalTypeDependencies();
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		SS streamCopy = (SS) stream.branch();
		ParseMatch<SS> match = theContent.parse(stream, parser, session);
		if(match != null && match.isComplete() && match.getError() == null)
			return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), Arrays.asList(match),
				"Forbidden content present", true, false);
		else
			return null;
	}
}
