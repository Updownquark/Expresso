package org.expresso.parse.debug;

import java.util.ArrayList;
import java.util.List;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

public class MatchData<S extends BranchableStream<?, ?>> {
	private final ParseMatcher<? super S> theMatcher;
	private final ParseSession theSession;
	private final List<List<ParseMatch<S>>> theMatches;
	private List<ParseMatch<S>> theCurrentMatches;
	private ParseMatch<S> theBestMatch;
	private boolean isCached;

	public MatchData(ParseMatcher<? super S> matcher, ParseSession session) {
		theMatcher = matcher;
		theSession = session;
		theMatches = new ArrayList<>();
	}

	public ParseMatcher<? super S> getMatcher() {
		return theMatcher;
	}

	public ParseSession getSession() {
		return theSession;
	}

	public void nextRound() {
		List<ParseMatch<S>> nextMatches = new ArrayList<>();
		theMatches.add(nextMatches);
		theCurrentMatches = nextMatches;
	}

	public boolean addMatch(ParseMatch<S> match) {
		if (theCurrentMatches == null)
			throw new IllegalStateException("Call nextRound() before addMatch(ParseMatch)");
		if(match!=null)
			match=filter(match);
		theCurrentMatches.add(match);
		boolean newBest = match != null && match.isBetter(theBestMatch);
		if (newBest)
			theBestMatch = match;
		return newBest;
	}

	private ParseMatch<S> filter(ParseMatch<S> match) {
		if(match.getLength()==0)
			return null;
		return match;
	}

	public ParseMatch<S> getBestMatch() {
		return theBestMatch;
	}

	public boolean isCached() {
		return isCached;
	}

	public void setCached(boolean cached) {
		this.isCached = cached;
	}

	@Override
	public String toString() {
		String matcherText = theMatcher.toString();
		int newLine = matcherText.indexOf('\n');
		if (newLine > 0)
			matcherText = matcherText.substring(0, newLine);
		return "Data for " + matcherText + ": " + (theBestMatch == null ? "none" : theBestMatch.flatText());
	}
}
