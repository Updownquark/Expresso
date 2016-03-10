package org.expresso.parse.matchers;

import java.util.Collections;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ParseMatcher;

/**
 * A base matcher class
 *
 * @param <S> The type of stream that this matcher can accept
 */
public abstract class BaseMatcher<S extends BranchableStream<?, ?>> implements ParseMatcher<S> {
	private final String theName;
	private final Set<String> theTags;

	/**
	 * @param name The name for the matcher
	 * @param tags The tags that may refer to this matcher in a parser
	 */
	protected BaseMatcher(String name, Set<String> tags) {
		theName = name;
		theTags = Collections.unmodifiableSet(tags);
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Set<String> getTags() {
		return theTags;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(getTypeName());
		if(theName != null)
			ret.append(" name=\"").append(theName).append('"');
		for(java.util.Map.Entry<String, String> attr : getAttributes().entrySet())
			ret.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
		if(!theTags.isEmpty()) {
			ret.append(" tag=\"");
			boolean first = true;
			for(String tag : theTags) {
				if(!first)
					ret.append(',');
				first = false;
				ret.append(tag);
			}
			ret.append('"');
		}
		for(ParseMatcher<? super S> composed : getComposed())
			ret.append("\n\t").append(composed.toString().replaceAll("\n", "\n\t"));
		return ret.toString();
	}
}
