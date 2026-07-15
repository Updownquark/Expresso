package org.observe.expresso.logging;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.qommons.QommonsUtils;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

public class LogReference extends AbstractList<String> {
	public static Set<LogReference> parse(LocatedPositionedContent refText, Function<LogReference, String> checkRef)
		throws QonfigInterpretationException {
		if (refText == null)
			return null;
		Set<LogReference> refs = new LinkedHashSet<>();
		List<String> path = new ArrayList<>();
		StringBuilder name = new StringBuilder();
		int pathStart = 0, pathEnd = -1;
		for (int c = 0; c < refText.length(); c++) {
			char ch = refText.charAt(c);
			switch (ch) {
			case ',':
				if (pathEnd < 0)
					pathEnd = c;
				path.add(name.toString());
				name.setLength(0);
				LogReference ref = new LogReference(path.toArray(new String[path.size()]));
				String msg = checkRef.apply(ref);
				if (msg != null)
					throw new QonfigInterpretationException(msg, refText.subSequence(pathStart, pathEnd));
				refs.add(ref);
				path.clear();
				break;
			case '.':
				if (pathEnd < 0)
					pathEnd = c;
				ref = new LogReference(path.toArray(new String[path.size()]));
				msg = checkRef.apply(ref);
				if (msg != null)
					throw new QonfigInterpretationException(msg, refText.subSequence(pathStart, pathEnd));
				path.add(name.toString());
				name.setLength(0);
				pathEnd = -1;
				break;
			default:
				if (Character.isWhitespace(ch)) {
					if (pathStart == c)
						pathStart++;
					else if (pathEnd < 0)
						pathEnd = c;
				} else
					name.append(ch);
			}
		}

		if (pathEnd < 0)
			pathEnd = refText.length();
		path.add(name.toString());
		LogReference ref = new LogReference(path.toArray(new String[path.size()]));
		String msg = checkRef.apply(ref);
		if (msg != null)
			throw new QonfigInterpretationException(msg, refText.subSequence(pathStart, pathEnd));
		refs.add(ref);
		return QommonsUtils.unmodifiableDistinctCopy(refs);
	}

	private final String[] theNames;

	private LogReference(String[] names) {
		theNames = names;
	}

	@Override
	public int size() {
		return theNames.length;
	}

	@Override
	public String get(int index) {
		return theNames[index];
	}

	public LogReference append(String name) {
		String[] newNames = new String[theNames.length + 1];
		System.arraycopy(theNames, 0, newNames, 0, theNames.length);
		newNames[theNames.length] = name;
		return new LogReference(newNames);
	}

	@Override
	public int hashCode() {
		return Objects.hash((Object[]) theNames);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof LogReference && Arrays.equals(theNames, ((LogReference) obj).theNames);
	}

	@Override
	public String toString() {
		return String.join(".", theNames);
	}
}
