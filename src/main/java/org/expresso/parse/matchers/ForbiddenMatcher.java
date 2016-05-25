package org.expresso.parse.matchers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.*;
import org.qommons.ex.ExIterable;

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
	public List<ParseMatcher<? super S>> getComposed() {
		return java.util.Collections.unmodifiableList(Arrays.asList(theContent));
	}

	@Override
	public <SS extends S> ExIterable<ParseMatch<SS>, IOException> match(SS stream, ExpressoParser<? super SS> parser,
			ParseSession session) {
		return parser.parseWith(stream, session, theContent).map(m -> {
			if (!m.isComplete() || m.getError() != null)
				return null;
			return new ParseMatch<>(this, stream, m.getLength(), Arrays.asList(m), "Forbidden content present", true);
		});
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(super.toString());
		ret.append("\n\t").append(theContent.toString().replaceAll("\n", "\n\t"));
		return ret.toString();
	}
}
