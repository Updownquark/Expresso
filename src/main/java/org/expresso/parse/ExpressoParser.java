package org.expresso.parse;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public interface ExpressoParser<S extends BranchableStream<?, ?>> extends ParseMatcher<S> {
	public static final String IGNORABLE = "ignorable";

	@Override
	default Set<String> getExternalTypeDependencies() {
		return Collections.EMPTY_SET; // This parser is self-contained
	}

	<SS extends S> ParseMatch<SS> parse(SS stream, String... types);

	default <SS extends S> Iterable<ParseMatch<SS>> matches(SS stream) {
		return new Iterable<ParseMatch<SS>>() {
			@Override
			public Iterator<ParseMatch<SS>> iterator() {
				return new Iterator<ParseMatch<SS>>() {
					@Override
					public boolean hasNext() {
						return !stream.isDiscovered() || stream.getDiscoveredLength() > 0;
					}

					@Override
					public ParseMatch<SS> next() {
						return parse(stream, ExpressoParser.this);
					}
				};
			}
		};
	}
}
