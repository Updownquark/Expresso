package org.expresso2;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.impl.BinarySequenceStream;
import org.expresso.parse.impl.CharSequenceStream;
import org.qommons.IntList;
import org.qommons.collect.BetterList;
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.config.QommonsConfig;

public class DefaultGrammarParser<S extends BranchableStream<?, ?>> implements GrammarParser<S> {
	public interface PreGrammar {
		int getTypeId(String typeName);

		List<String> getClassMembers(String className);
	}

	public interface RecognizedComponent<S extends BranchableStream<?, ?>> {
		ExpressionComponent<S> build(int id, Map<String, String> config, String value, String untrimmedValue,
			List<ExpressionComponent<S>> children, PreGrammar grammar);
	}

	private final Map<String, RecognizedComponent<S>> theRecognizedComponents;

	public DefaultGrammarParser(Map<String, RecognizedComponent<S>> recognizedComponents) {
		theRecognizedComponents = recognizedComponents;
	}

	@Override
	public ExpressoGrammar<S> parseGrammar(String name, InputStream stream) throws IOException {
		QommonsConfig config = QommonsConfig.fromXml(QommonsConfig.getRootElement(stream));
		if (!config.getName().equals("expresso"))
			throw new IllegalArgumentException("expresso expected as root, not " + config.getName());
		int[] id = new int[1];
		class PreParsedClass {
			final ExpressionClass<S> clazz;
			final List<String> memberNames;
			final List<ExpressionType<S>> types;

			PreParsedClass(String className) {
				types = new LinkedList<>();
				memberNames = new LinkedList<>();
				clazz = new ExpressionClass<>(id[0]++, className, Collections.unmodifiableList(types));
			}

			ExpressionClass<S> addType(String typeName) {
				memberNames.add(typeName);
				return clazz;
			}
		}
		Map<String, PreParsedClass> declaredClasses = new LinkedHashMap<>();
		Map<String, TypeReference<S>> declaredTypes = new LinkedHashMap<>();
		// One run-though to populate the type references
		for (QommonsConfig type : config.subConfigs()) {
			if (!type.getName().equals("expression"))
				throw new IllegalArgumentException("expression elements expected, not " + type.getName());
			String typeName = type.get("name");
			if (typeName == null)
				throw new IllegalArgumentException("expression has no name: " + type);
			else if (theRecognizedComponents.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " is a reserved component type name: " + type);
			else if (declaredTypes.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " has already been declared as an expression: " + type);
			else if (declaredClasses.containsKey(typeName))
				throw new IllegalArgumentException(typeName + " has already been declared as a class: " + type);
			String classesStr = type.get("class");
			List<ExpressionClass<S>> classes;
			if (classesStr != null) {
				String[] classesSplit = classesStr.split(",");
				classes = new ArrayList<>(classesSplit.length);
				for (String clazz : classesSplit) {
					classes.add(declaredClasses.computeIfAbsent(clazz.trim(), c -> {
						if (theRecognizedComponents.containsKey(c))
							throw new IllegalArgumentException(c + " is a reserved component type name: " + type);
						else if (declaredTypes.containsKey(c))
							throw new IllegalArgumentException(c + " has already been declared as an expression: " + type);
						return new PreParsedClass(c);
					}).addType(typeName));
				}
				classes = Collections.unmodifiableList(classes);
			} else
				classes = Collections.emptyList();
			Set<String> fieldNames = new LinkedHashSet<>();
			for (QommonsConfig componentConfig : type.subConfigs()) {
				if (componentConfig.getName().equals("name") || componentConfig.equals("class"))
					continue;
				String field = componentConfig.get("field");
				if (field != null)
					fieldNames.add(field);
			}
			int priority = type.getInt("priority", 0);
			if (declaredTypes.put(typeName, new TypeReference<>(id[0]++, priority, typeName, classes, ParameterSet.of(fieldNames))) != null)
				throw new IllegalArgumentException("Duplicate expressions named " + typeName);
		}
		Map<String, ExpressionClass<S>> classes = new LinkedHashMap<>(declaredClasses.size() * 4 / 3);
		for (PreParsedClass clazz : declaredClasses.values())
			classes.put(clazz.clazz.getName(), clazz.clazz);
		PreGrammar preGrammar = new PreGrammar() {
			@Override
			public int getTypeId(String typeName) {
				TypeReference<S> type = declaredTypes.get(typeName);
				if (type == null)
					throw new IllegalArgumentException("Unrecognized type name: " + typeName);
				return type.id;
			}

			@Override
			public List<String> getClassMembers(String className) {
				PreParsedClass clazz = declaredClasses.get(className);
				if (clazz == null)
					throw new IllegalArgumentException("Unrecognized class name: " + className);
				return clazz.memberNames;
			}
		};
		// Second run-through to initialize all the types
		for (QommonsConfig type : config.subConfigs()) {
			TypeReference<S> typeRef = declaredTypes.get(type.get("name"));
			QommonsConfig[] componentConfigs = type.subConfigs();
			List<ExpressionComponent<S>> components = new ArrayList<>(componentConfigs.length//
				- (typeRef.classes.isEmpty() ? 1 : 2)); // "name" and "class"
			ParameterMap<BetterList<ExpressionComponent<S>>> fields;
			fields = ParameterSet.EMPTY.createMap();
			// fields = typeRef.fieldNames.createMap();
			for (QommonsConfig componentConfig : componentConfigs) {
				if (componentConfig.getName().equals("name") || componentConfig.getName().equals("class")
					|| componentConfig.getName().equals("priority"))
					continue;
				ExpressionComponent<S> component;
				try {
					component = parseComponent(componentConfig, classes, declaredTypes, true, id, preGrammar);
				} catch (RuntimeException e) {
					throw new IllegalStateException(e.getMessage() + ":\n" + type, e);
				}
				components.add(component);
				/*String field = componentConfig.get("field");
				if (field != null) {
					BetterList<ExpressionComponent<S>> preField = fields.get(field);
					ExpressionComponent<S>[] preFieldValues = new ExpressionComponent[preField == null ? 1 : preField.size() + 1];
					for (int i = 0; i < preFieldValues.length - 1; i++)
						preFieldValues[i] = preField.get(i);
					preFieldValues[preFieldValues.length - 1] = component;
					fields.put(field, BetterList.of(preFieldValues));
				}*/
			}
			typeRef.initialize(Collections.unmodifiableList(components), fields.unmodifiable());
			for (ExpressionClass<S> clazz : typeRef.classes)
				declaredClasses.get(clazz.getName()).types.add(typeRef.type);
		}
		List<ExpressionType<S>> types = new ArrayList<>(declaredTypes.size());
		Map<String, ExpressionType<S>> typesByName = new LinkedHashMap<>(declaredTypes.size() * 4 / 3);
		for (TypeReference<S> typeRef : declaredTypes.values()) {
			types.add(typeRef.type);
			typesByName.put(typeRef.name, typeRef.type);
		}
		return new ExpressoGrammar<>(name, Collections.unmodifiableList(types), Collections.unmodifiableMap(typesByName),
			Collections.unmodifiableMap(classes));
	}

	private ExpressionComponent<S> parseComponent(QommonsConfig config, Map<String, ExpressionClass<S>> classes,
		Map<String, TypeReference<S>> types, boolean mayBeField, int[] id, PreGrammar grammar) {
		String componentType = config.getName();
		RecognizedComponent<S> rc = theRecognizedComponents.get(componentType);
		if (rc != null) {
			Map<String, String> params = new LinkedHashMap<>();
			String value = config.getValue();
			if ("".equals(value))
				value = null;
			String untrimmedValue = config.getValueUntrimmed();
			QommonsConfig[] subComponentConfigs = config.subConfigs();
			int componentId = id[0]++;
			List<ExpressionComponent<S>> subComponents = new ArrayList<>(subComponentConfigs.length);
			for (QommonsConfig subComponentConfig : subComponentConfigs) {
				if (subComponentConfig.getName().equals("field")) {
					if (mayBeField)
						continue;
					throw new IllegalArgumentException("Configuration point \"field\" is a reserved word: " + config);
				} else if (subComponentConfig.getName().equals("tag"))
					continue; // Tags not supported yet
				if (subComponentConfig.subConfigs().length > 0 || subComponentConfig.getValue() == null
					|| theRecognizedComponents.containsKey(subComponentConfig.getName()))
					subComponents.add(parseComponent(subComponentConfig, classes, types, mayBeField, id, grammar));
				else
					params.put(subComponentConfig.getName(), subComponentConfig.getValue());
			}
			if (subComponents.isEmpty())
				subComponents = Collections.emptyList();
			else {
				((ArrayList<?>) subComponents).trimToSize();
				subComponents = Collections.unmodifiableList(subComponents);
			}
			ExpressionComponent<S> component;
			try {
				component = rc.build(componentId, params, value, untrimmedValue, subComponents, grammar);
			} catch (RuntimeException e) {
				throw new IllegalStateException(e.getMessage() + ":\n" + config, e);
			}
			if (!params.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration point" + (params.size() == 1 ? "" : "s") + ": "
					+ componentType + "." + params.keySet() + " in " + config);
			return component;
		}
		TypeReference<S> type = types.get(componentType);
		if (type != null)
			return type;
		ExpressionClass<S> clazz = classes.get(componentType);
		if (clazz != null)
			return clazz;
		throw new IllegalArgumentException("Unrecognized component type: " + componentType + " in " + config);
	}

	/** @return The standard text GrammarParser */
	public static GrammarParser<CharSequenceStream> forText() {
		Map<String, RecognizedComponent<CharSequenceStream>> components = getCommonComponents();
		components.put("literal", (id, config, value, untrimmedValue, children, grammar) -> {
			if (!children.isEmpty())
				throw new IllegalArgumentException("Literal cannot have children");
			else if (untrimmedValue == null)
				throw new IllegalArgumentException("Literal declared with no content");
			else if (value != null)
				return new TextLiteralExpression<>(id, value);
			else
				return new TextLiteralExpression<>(id, untrimmedValue);
		});
		components.put("pattern", (id, config, value, untrimmedValue, children, grammar) -> {
			String ciStr = config.remove("case-sensitive");
			boolean ci;
			if (ciStr == null || ciStr.equals("false"))
				ci = false;
			else if (ciStr.equals("true"))
				ci = true;
			else
				throw new IllegalArgumentException("Unrecognized value for configuration case-sensitive: " + ciStr);
			String maxLen = config.remove("max-length");
			if (!children.isEmpty())
				throw new IllegalArgumentException("Pattern cannot have children");
			else if (value == null)
				throw new IllegalArgumentException("Pattern declared with no content");
			Pattern pattern = Pattern.compile(value, ci ? Pattern.CASE_INSENSITIVE : 0);
			return new TextPatternExpressionType<>(id, maxLen == null ? Integer.MAX_VALUE : Integer.parseInt(maxLen), pattern);
		});
		return new DefaultGrammarParser<>(components);
	}

	/** @return The standard binary GrammarParser (not yet implemented) */
	public static GrammarParser<BinarySequenceStream> forBinary() {
		// Map<String, RecognizedComponent<CharSequenceStream>> components = getCommonComponents();
		// TODO Create and add a binary literal expression type and possibly some sort of binary pattern matcher
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private static <S extends BranchableStream<?, ?>> Map<String, RecognizedComponent<S>> getCommonComponents() {
		Map<String, RecognizedComponent<S>> components = new HashMap<>();
		components.put("sequence", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Sequence declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Sequence declared with text content: " + value);
			else
				return new SequenceExpression<>(id, children);
		});
		components.put("one-of", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("One-of declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("One-of declared with text content: " + value);
			else
				return new OneOfExpression<>(id, children);
		});
		components.put("repeat", (id, config, value, untrimmedValue, children, grammar) -> {
			String minStr = config.remove("min");
			String maxStr = config.remove("max");
			if (children.isEmpty())
				throw new IllegalArgumentException("Repeat declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Repeat declared with text content: " + value);
			else
				return new RepeatExpression<>(id, //
					minStr == null ? 0 : Integer.parseInt(minStr), //
					maxStr == null ? Integer.MAX_VALUE : Integer.parseInt(maxStr), //
					children);
		});
		components.put("option", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Option declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Option declared with text content: " + value);
			else
				return new OptionalExpressionType<>(id, children);
		});
		components.put("forbid", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Forbid declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Forbid declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Forbid declared with text content: " + value);
			else
				return new ForbidExpressionComponent<>(id, children.get(0));
		});
		components.put("up-to", (id, config, value, untrimmedValue, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Up-to declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Up-to declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Up-to declared with text content: " + value);
			else
				return new LeadUpExpressionComponent<>(id, children.get(0));
		});
		components.put("without", (id, config, value, untrimmedValue, children, grammar) -> {
			String typeStr = config.remove("types");
			String classStr = config.remove("classes");
			if (typeStr == null && classStr == null)
				throw new IllegalArgumentException("Without declared with no excluded types or classes: " + config);
			else if (children.isEmpty())
				throw new IllegalArgumentException("Without declared with no children: " + children);
			else if (value != null)
				throw new IllegalArgumentException("Without declared with text content: " + value);
			String[] splitTypes = typeStr == null ? new String[0] : typeStr.split(",");
			String[] splitClasses = classStr == null ? new String[0] : classStr.split(",");
			IntList excludedIds = new IntList(splitTypes.length + splitClasses.length);
			excludedIds.setSorted(true);
			excludedIds.setUnique(true);
			for (String type : splitTypes) {
				excludedIds.add(grammar.getTypeId(type));
			}
			for (String clazz : splitClasses) {
				for (String type : grammar.getClassMembers(clazz))
					excludedIds.add(grammar.getTypeId(type));
			}
			return new ExcludeExpressionComponent<>(id, excludedIds.toArray(), children);
		});
		return components;
	}

	private static class TypeReference<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
		private final int priority;
		private final String name;
		final List<ExpressionClass<S>> classes;
		ExpressionType<S> type;

		TypeReference(int id, int priority, String name, List<ExpressionClass<S>> classes, ParameterSet fieldNames) {
			super(id);
			this.priority = priority;
			this.name = name;
			this.classes = classes;
		}

		void initialize(List<ExpressionComponent<S>> components, ParameterMap<BetterList<ExpressionComponent<S>>> fields) {
			type = new ExpressionType<>(id, priority, name, classes, components, fields);
		}

		@Override
		public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session) throws IOException {
			return type.parse(session);
		}
	}
}
