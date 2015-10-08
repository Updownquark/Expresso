package org.expresso.parse.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseSession;
import org.expresso.parse.impl.WithoutMatcher.ExcludedTypesParser;

/**
 * Counters the effect of {@link WithoutMatcher}
 *
 * @param <S> The type of stream to parse
 */
public class WithMatcher<S extends BranchableStream<?, ?>> extends SequenceMatcher<S> {
	private final Set<String> theIncludedTypes;

	/** @param types The types to include */
	public WithMatcher(String... types) {
		super(null, Collections.EMPTY_SET);
		theIncludedTypes = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(types)));
	}

	@Override
	public <SS extends S> ParseMatch<SS> parse(SS stream, ExpressoParser<? super SS> parser, ParseSession session) {
		ExpressoParser<? super SS> unfilteredParser;
		if(!(parser instanceof ExcludedTypesParser))
			return super.parse(stream, parser, session);
		else {
			ExcludedTypesParser<? super SS> filteredParser = (ExcludedTypesParser<? super SS>) parser;
			Set<String> toReExclude = new LinkedHashSet<>();
			for(String include : theIncludedTypes) {
				if(filteredParser.getExcludedTypes().remove(include))
					toReExclude.add(include);
				else
					System.out.println("WARNING: " + include + " not excluded from parsing");
			}
			ParseMatch<SS> ret = super.parse(stream, parser, session);
			filteredParser.getExcludedTypes().addAll(toReExclude);
			return ret;
		}
	}
}
