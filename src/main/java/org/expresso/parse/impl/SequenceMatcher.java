package org.expresso.parse.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;

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
	public String getTypeName() {
		return "sequence";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
		Set<String> ret = new java.util.LinkedHashSet<>();
		for(ParseMatcher<?> sub : getComposed()) {
			ret.addAll(sub.getPotentialBeginningTypeReferences(parser, session));
			if(sub instanceof ForbiddenMatcher)
				continue;
			else if(sub instanceof RepeatingSequenceMatcher && ((RepeatingSequenceMatcher<?>) sub).getMin() == 0)
				continue;
			else
				break;
		}
		return ret;
	}

	@Override
	public <SS extends S> List<ParseMatch<SS>> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session)
			throws IOException {
		if (getComposed().isEmpty())
			return Collections.EMPTY_LIST;
		List<List<ParseMatch<SS>>> paths = new ArrayList<>();
		SS copy = (SS) stream.branch();
		for (ParseMatch<SS> possibility : parser.parseWith(copy, session, getComposed().get(0))) {
			List<ParseMatch<SS>> path = new ArrayList<>();
			path.add(possibility);
			paths.add(path);
		}
		for (int i = 1; i < getComposed().size(); i++) {
			int todo = todo; // Refactor to use parseNext()
			ParseMatcher<? super S> element = getComposed().get(i);
			parseNext(stream, s -> parser.parseWith(s, session, element), paths, i);
			ListIterator<List<ParseMatch<SS>>> pathIter = paths.listIterator();
			while (pathIter.hasNext()) {
				List<ParseMatch<SS>> path = pathIter.next();
				if (path.size() != i || !path.get(path.size() - 1).isComplete())
					continue;

				copy = (SS) stream.branch();
				for (ParseMatch<SS> pathEl : path)
					copy.advance(pathEl.getLength());
				List<ParseMatch<SS>> newMatches = parser.parseWith(copy, session, element);
				if (newMatches.isEmpty())
					pathIter.remove();
				else {
					// Re-use the path list for the first match
					path.add(newMatches.get(newMatches.size() - 1));
					for (int j = 0; j < newMatches.size() - 1; j++) {
						List<ParseMatch<SS>> newPath = new ArrayList<>();
						// The path now has the first new match in it, so we can't just do addAll
						for (int k = 0; k < path.size() - 1; k++)
							newPath.add(path.get(k));
						newPath.add(newMatches.get(j));
						pathIter.add(newPath);
					}
				}
			}
		}

		copy = (SS) stream.branch();
		List<ParseMatch<SS>> ret = new ArrayList<>(paths.size());
		for (int i = 0; i < ret.size(); i++) {
			List<ParseMatch<SS>> path = paths.get(i);
			String errorMsg;
			if (!path.isEmpty() && path.get(path.size() - 1).getError() != null)
				errorMsg = null; // Let the deeper message come up
			else if (path.size() < getComposed().size())
				errorMsg = "Expected " + getComposed().get(path.size());
			else
				errorMsg = null;
			int length = 0;
			for (ParseMatch<SS> el : path)
				length += el.getLength();
			ret.add(new ParseMatch<>(this, copy, length, path, errorMsg, path.size() == getComposed().size()));
		}
		return ret;
	}

	protected interface MatchParser<S extends BranchableStream<?, ?>> {
		List<ParseMatch<S>> parse(S stream) throws IOException;
	}

	protected <SS extends S> boolean parseNext(SS stream, MatchParser<SS> parser, List<List<ParseMatch<SS>>> paths,
			int depth) throws IOException {
		boolean madeProgress = false;
		ListIterator<List<ParseMatch<SS>>> pathIter = paths.listIterator();
		while (pathIter.hasNext()) {
			List<ParseMatch<SS>> path = pathIter.next();
			if (path.size() != depth || !path.get(path.size() - 1).isComplete())
				continue;

			madeProgress = true;
			SS copy = (SS) stream.branch();
			for (ParseMatch<SS> pathEl : path)
				copy.advance(pathEl.getLength());
			List<ParseMatch<SS>> newMatches = parser.parse(copy);
			if (newMatches.isEmpty())
				pathIter.remove();
			else {
				// Re-use the path list for the first match
				path.add(newMatches.get(newMatches.size() - 1));
				for (int j = 0; j < newMatches.size() - 1; j++) {
					List<ParseMatch<SS>> newPath = new ArrayList<>();
					// The path now has the first new match in it, so we can't just do addAll
					for (int k = 0; k < path.size() - 1; k++)
						newPath.add(path.get(k));
					newPath.add(newMatches.get(j));
					pathIter.add(newPath);
				}
			}
		}
		return madeProgress;
	}

	/**
	 * @param <S> The type of stream to accommodate
	 * @param name The name for the new matcher
	 * @return A builder to create a new sequence matcher
	 */
	public static <S extends BranchableStream<?, ?>> Builder<S, SequenceMatcher<S>> buildSequence(String name) {
		return new Builder<>(name);
	}

	/**
	 * Builds {@link SequenceMatcher}s
	 *
	 * @param <S> The type of stream to accommodate
	 * @param <M> The sub-type of sequence matcher to build
	 */
	public static class Builder<S extends BranchableStream<?, ?>, M extends SequenceMatcher<S>> extends ComposedMatcher.Builder<S, M> {
		/** @param name The name for the matcher */
		protected Builder(String name) {
			super(name);
		}

		@Override
		public Builder<S, M> tag(String... tags) {
			return (Builder<S, M>) super.tag(tags);
		}

		@Override
		public Builder<S, M> addChild(ParseMatcher<? super S> child) {
			return (Builder<S, M>) super.addChild(child);
		}

		@Override
		protected M create(String name, Set<String> tags) {
			return (M) new SequenceMatcher<>(name, tags);
		}
	}
}
