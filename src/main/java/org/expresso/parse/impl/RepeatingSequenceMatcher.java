package org.expresso.parse.impl;

import java.util.List;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.qommons.ArrayUtils;

public class RepeatingSequenceMatcher<S extends BranchableStream<?, ?>> extends SequenceMatcher<S> {
	private final int theMinRepeat;
	private final int theMaxRepeat;

	protected RepeatingSequenceMatcher(String name, Set<String> tags, int minRepeat, int maxRepeat) {
		super(name, tags);
		theMinRepeat = minRepeat;
		theMaxRepeat = maxRepeat;
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser) {
		SS streamCopy = (SS) stream.branch();
		int prePosition = stream.getPosition();
		int count = 0;
		ParseMatch<S> match = null;
		int preOptionIndex = index;
		List<ParseMatch<S>> optionMatches = null;
		int optStartIndex = index;
		theDebugger.preParse(sb, index, sub);
		while(theMaxRepeat < 0 || count < theMaxRepeat) {
			match = super.<SS> parse(stream, parser);
			if(match == null || !match.isComplete() || match.getError() != null) {
				if(match != null && badOptionOld) {
					badOption = match;
					badOptionOld = false;
				}
				break;
			}
			optionMatches.add(match);
			index += match.text.length();
			count++;
		}
		if(count < min) {
			if(!badOptionOld) {
				if(optionMatches != null)
					for(ParseMatch optMatch : optionMatches)
						subMatches = ArrayUtils.addAll(optMatch.getParsed());
				match = badOption;
			} else {
				String name = sub.get("storeAs");
				if(name == null)
					name = "option";
				match = new ParseMatch(sub, "", index, null, false,
					"At least " + min + " \"" + name + "\" occurrence" + (min > 1 ? "s" : "") + " expected");
			}
			theDebugger.postParse(sb, optStartIndex, sub, match);
			break; // handle this outside the switch
		}
	}
}
