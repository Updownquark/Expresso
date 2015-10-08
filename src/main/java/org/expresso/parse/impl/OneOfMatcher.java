package org.expresso.parse.impl;

import java.util.Arrays;
import java.util.Set;

import org.expresso.parse.*;

/**
 * Acts as a switch. Matches any one of a set of matchers against a stream.
 *
 * @param <S> The type of stream to parse
 */
public class OneOfMatcher<S extends BranchableStream<?, ?>> extends ComposedMatcher<S> {
	/** @see ComposedMatcher#ComposedMatcher(String, Set) */
	protected OneOfMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		ParseMatch<SS> match = null;
		for(ParseMatcher<? super S> element : getComposed()) {
			SS streamCopy = (SS) stream.branch();
			ParseMatch<SS> optionMatch = element.parse(streamCopy, parser, session);
			if(optionMatch == null)
				continue;
			else if(optionMatch.isComplete() && optionMatch.getError() == null) {
				match = optionMatch;
				break;
			} else if(optionMatch.isBetter(match)) {
				// if(match != null)
				// theDebugger.matchDiscarded(match);
				match = optionMatch;
			}
		}
		if(match == null)
			return null;
		SS streamCopy = (SS) stream.branch();
		stream.advance(match.getLength());
		return new ParseMatch<>(this, streamCopy, match.getLength(), Arrays.asList(match), null, true, false);
	}
}
