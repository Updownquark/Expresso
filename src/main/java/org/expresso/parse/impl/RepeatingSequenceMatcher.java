package org.expresso.parse.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseSession;

/**
 * Matches a sequence of other matchers optionally or multiple times
 *
 * @param <S> The type of stream this matcher can parse
 */
public class RepeatingSequenceMatcher<S extends BranchableStream<?, ?>> extends SequenceMatcher<S> {
	private final int theMinRepeat;
	private final int theMaxRepeat;

	/**
	 * @param name The name for this matcher
	 * @param tags The tags that this matcher can be referenced by
	 * @param minRepeat The minimum number of times that this matcher must match content in a stream sequentially to be valid
	 * @param maxRepeat The maximum number of times that this matcher will attempt to match its content in a stream sequentially
	 */
	protected RepeatingSequenceMatcher(String name, Set<String> tags, int minRepeat, int maxRepeat) {
		super(name, tags);
		theMinRepeat = minRepeat;
		theMaxRepeat = maxRepeat;
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		SS streamCopy = (SS) stream.branch();
		int count = 0;
		boolean optionsComplete = true;
		List<ParseMatch<SS>> optionMatches = new ArrayList<>();
		while(theMaxRepeat < 0 || count < theMaxRepeat) {
			ParseMatch<SS> match = super.parse(stream, parser, session);
			if(match == null)
				break;
			optionMatches.add(match);
			count++;
			if(!match.isComplete()) {
				optionsComplete = true;
				break;
			}
		}

		String error = null;
		if(optionsComplete && count < theMinRepeat)
			error = "At least " + theMinRepeat + " repetition" + (theMinRepeat > 1 ? "s" : "") + " expected";

		return new ParseMatch<>(this, streamCopy, stream.getPosition() - streamCopy.getPosition(), optionMatches, error, error == null,
			false);
	}
}
