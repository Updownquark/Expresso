package org.expresso.parse;

import java.util.Set;

public interface ParseSession {
	void excludeTypes(String... types);

	void includeTypes(String... types);

	Set<String> getExcludedTypes();
}
