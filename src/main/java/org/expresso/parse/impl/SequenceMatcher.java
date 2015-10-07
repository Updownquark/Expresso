package org.expresso.parse.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;

public class SequenceMatcher<S extends BranchableStream<?, ?>> extends ComposedMatcher<S> {
	protected SequenceMatcher(String name, Set<String> tags) {
		super(name, tags);
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser) {
		SS streamCopy = (SS) stream.branch();
		int prePosition = stream.getPosition();
		List<ParseMatch<SS>> components = new ArrayList<>();
		ParseMatch<SS> ignorable;
		do {
			ignorable = parser.parse(stream, ExpressoParser.IGNORABLE);
			if(ignorable != null)
				components.add(ignorable);
		} while(ignorable != null);
		boolean complete = true;
		for(ParseMatcher<? super S> element : getComposed()) {
			ParseMatch<SS> component = element.<SS> parse(stream, parser);
			if(component == null) {
				complete = false;
				break;
			} else if(!component.isComplete())
				break;
			components.add(component);
		}
		if(components.isEmpty())
			return null;
		int postPosition = stream.getPosition();
		return new ParseMatch<>(this, streamCopy, postPosition - prePosition, components, null, complete);
	}
}
