package org.expresso.parse.matchers;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.expresso.parse.ParseMatch;
import org.expresso.parse.impl.CharSequenceStream;

/**
 * Matches characters in a stream to a java {@link Pattern}
 *
 * @param <S> The type of stream to parse
 */
public class TextPatternMatcher<S extends CharSequenceStream> extends SimpleValueMatcher<S> {
	private final Pattern thePattern;

	/**
	 * @param name The name of this matcher
	 * @param tags The tags by which this pattern may be referenced in a parser
	 * @param pattern The pattern for this matcher to match against
	 */
	public TextPatternMatcher(String name, Set<String> tags, Pattern pattern) {
		super(name, tags);
		if(pattern.pattern().length() == 0)
			throw new IllegalArgumentException("Text pattern matchers cannot search for empty strings");
		thePattern = pattern;
	}

	@Override
	public String getTypeName() {
		return "pattern";
	}

	@Override
	public Map<String, String> getAttributes() {
		return java.util.Collections.EMPTY_MAP;
	}

	@Override
	public <SS extends S> ParseMatch<SS> parseValue(SS stream) {
		Matcher matcher = thePattern.matcher(stream);
		if(!matcher.lookingAt())
			return null;
		return new ParseMatch<>(this, (SS) stream.branch(), matcher.end(), java.util.Collections.EMPTY_LIST, null, true);
	}

	@Override
	public String getValueString() {
		return thePattern.toString();
	}

	@Override
	public String toString() {
		return super.toString() + " pattern=\"" + thePattern.toString() + "\"";
	}
}
