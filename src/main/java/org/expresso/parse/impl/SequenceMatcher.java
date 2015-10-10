package org.expresso.parse.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.expresso.parse.*;

/**
 * Matches a sequence of other matchers
 *
 * @param <S> The type of stream this matcher can parse
 */
public class SequenceMatcher<S extends BranchableStream<?, ?>> extends ComposedMatcher<S> {
	/** @see ComposedMatcher#ComposedMatcher(String, Set) */
	protected SequenceMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public <SS extends S> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		SS streamCopy = (SS) stream.branch();
		List<ParseMatch<SS>> components = new ArrayList<>();
		ParseMatcher<? super S> missingEl = null;
		for(ParseMatcher<? super S> element : getComposed()) {
			ParseMatch<SS> component = parser.parse(stream, session, element);
			if(component == null) {
				missingEl = element;
				break;
			}
			components.add(component);
			if(!component.isComplete())
				break;
		}
		if(components.isEmpty())
			return null;
		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), components,
			missingEl == null ? null : "Expected " + missingEl, missingEl == null);
	}
}
