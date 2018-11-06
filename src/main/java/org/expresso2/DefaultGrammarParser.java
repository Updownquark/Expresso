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
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.config.QommonsConfig;

public class DefaultGrammarParser<S extends BranchableStream<?, ?>> implements GrammarParser<S> {
	public interface PreGrammar {
		int getTypeId(String typeName);

		List<String> getClassMembers(String className);
	}

	public interface RecognizedComponent<S extends BranchableStream<?, ?>> {
		ExpressionComponent<S> build(int id, Map<String, String> config, String value, List<ExpressionComponent<S>> children,
			PreGrammar grammar);
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
				throw new IllegalArgumentException("expression has no name");
			String classesStr = type.get("class");
			List<ExpressionClass<S>> classes;
			if (classesStr != null) {
				String[] classesSplit = classesStr.split(",");
				classes = new ArrayList<>(classesSplit.length);
				for (String clazz : classesSplit)
					classes.add(declaredClasses.computeIfAbsent(clazz.trim(), c -> new PreParsedClass(c)).addType(typeName));
				classes = Collections.unmodifiableList(classes);
			} else
				classes = Collections.emptyList();
			Set<String> fieldNames = new LinkedHashSet<>();
			for (QommonsConfig componentConfig : type.subConfigs()) {
				if (componentConfig.getName().equals("name") || componentConfig.equals("class"))
					continue;
				String field = componentConfig.get("field");
				if (field != null && !fieldNames.add(field))
					throw new IllegalArgumentException("Field " + typeName + "." + field + " is declared more than once");
			}
			if (declaredTypes.put(typeName, new TypeReference<>(id[0]++, typeName, classes, ParameterSet.of(fieldNames))) != null)
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
			ParameterMap<ExpressionComponent<S>> fields = typeRef.fieldNames.createMap();
			for (QommonsConfig componentConfig : componentConfigs) {
				if (componentConfig.getName().equals("name") || componentConfig.equals("class"))
					continue;
				ExpressionComponent<S> component = parseComponent(componentConfig, classes, declaredTypes, true, id, preGrammar);
				components.add(component);
				String field = componentConfig.get("field");
				if (field != null)
					fields.put(field, component);
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
		if (componentType.equals("type")) {
			// TODO
		} else if (componentType.equals("class")) {
			// TODO
		}
		RecognizedComponent<S> rc = theRecognizedComponents.get(componentType);
		if (rc == null)
			throw new IllegalArgumentException("Unsupported component type: " + componentType);

		Map<String, String> params = new LinkedHashMap<>();
		String value = config.getValue().trim();
		if ("".equals(value))
			value = null;
		QommonsConfig[] subComponentConfigs = config.subConfigs();
		int componentId = id[0]++;
		List<ExpressionComponent<S>> subComponents = new ArrayList<>(subComponentConfigs.length);
		for (QommonsConfig subComponentConfig : subComponentConfigs) {
			if (subComponentConfig.getName().equals("field")) {
				if (mayBeField)
					continue;
				throw new IllegalArgumentException("Configuration point \"field\" is a reserved word");
			}
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
		ExpressionComponent<S> component = rc.build(componentId, params, value, subComponents, grammar);
		if (!params.isEmpty())
			throw new IllegalArgumentException(
				"Unsupported configuration point" + (params.size() == 1 ? "" : "s") + ": " + componentType + "." + params.keySet());
		return component;
	}

	public static GrammarParser<CharSequenceStream> forText() {
		Map<String, RecognizedComponent<CharSequenceStream>> components = getCommonComponents();
		components.put("literal", (id, config, value, children, grammar) -> {
			if (!children.isEmpty())
				throw new IllegalArgumentException("Literal cannot have children");
			else if (value == null)
				throw new IllegalArgumentException("Literal declared with no content");
			else
				return new TextLiteralExpression<>(value);
		});
		components.put("pattern", (id, config, value, children, grammar) -> {
			String ciStr = config.remove("case-sensitive");
			boolean ci;
			if (ciStr == null || ciStr.equals("false"))
				ci = false;
			else if (ciStr.equals("true"))
				ci = true;
			else
				throw new IllegalArgumentException("Unrecognized value for configuration case-sensitive: " + ciStr);
			if (!children.isEmpty())
				throw new IllegalArgumentException("Pattern cannot have children");
			else if (value == null)
				throw new IllegalArgumentException("Pattern declared with no content");
			Pattern pattern = Pattern.compile(value, ci ? Pattern.CASE_INSENSITIVE : 0);
			return new TextPatternExpressionType<>(pattern);
		});
		return new DefaultGrammarParser<>(components);
	}

	public static GrammarParser<BinarySequenceStream> forBinary() {
		Map<String, RecognizedComponent<CharSequenceStream>> components = getCommonComponents();
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private static <S extends BranchableStream<?, ?>> Map<String, RecognizedComponent<S>> getCommonComponents() {
		Map<String, RecognizedComponent<S>> components = new HashMap<>();
		components.put("sequence", (id, config, value, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Sequence declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Sequence declared with text content: " + value);
			else
				return new SequenceExpression<>(id, children);
		});
		components.put("one-of", (id, config, value, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("One-of declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("One-of declared with text content: " + value);
			else
				return new OneOfExpression<>(id, children);
		});
		components.put("repeat", (id, config, value, children, grammar) -> {
			String minStr = config.remove("min");
			String maxStr = config.remove("max");
			if (children.isEmpty())
				throw new IllegalArgumentException("Repeat declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Repeat declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Repeat declared with text content: " + value);
			else
				return new RepeatExpression<>(id, //
					minStr == null ? 0 : Integer.parseInt(minStr), //
					maxStr == null ? Integer.MAX_VALUE : Integer.parseInt(maxStr), //
					children.get(0));
		});
		components.put("option", (id, config, value, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Option declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Option declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Option declared with text content: " + value);
			else
				return new OptionalExpressionType<>(id, children.get(0));
		});
		components.put("forbid", (id, config, value, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Forbid declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Forbid declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Forbid declared with text content: " + value);
			else
				return new ForbidExpressionComponent<>(id, children.get(0));
		});
		components.put("up-to", (id, config, value, children, grammar) -> {
			if (children.isEmpty())
				throw new IllegalArgumentException("Up-to declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Up-to declared with multiple children");
			else if (value != null)
				throw new IllegalArgumentException("Up-to declared with text content: " + value);
			else
				return new LeadUpExpressionComponent<>(id, children.get(0));
		});
		components.put("without", (id, config, value, children, grammar) -> {
			String typeStr = config.remove("types");
			String classStr = config.remove("classes");
			if (typeStr == null && classStr == null)
				throw new IllegalArgumentException("Without declared with no excluded types or classes");
			else if (children.isEmpty())
				throw new IllegalArgumentException("Without declared with no children");
			else if (children.size() > 1)
				throw new IllegalArgumentException("Without declared with multiple children");
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
			return new ExcludeExpressionComponent<>(id, excludedIds.toArray(), children.get(0));
		});
		return components;
	}

	private static class TypeReference<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
		private final String name;
		final List<ExpressionClass<S>> classes;
		final ParameterSet fieldNames;
		ExpressionType<S> type;

		TypeReference(int id, String name, List<ExpressionClass<S>> classes, ParameterSet fieldNames) {
			super(id);
			this.name = name;
			this.classes = classes;
			this.fieldNames = fieldNames;
		}

		void initialize(List<ExpressionComponent<S>> components, ParameterMap<ExpressionComponent<S>> fields) {
			type = new ExpressionType<>(id, name, classes, components, fields);
		}

		@Override
		public <S2 extends S> ExpressionPossibility<S2> tryParse(ExpressoParser<S2> session) {
			return type.tryParse(session);
		}
	}
}
