package org.expresso2;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.impl.BinarySequenceStream;
import org.expresso.parse.impl.CharSequenceStream;
import org.qommons.config.QommonsConfig;

public class DefaultGrammarParser<S extends BranchableStream<?, ?>> implements GrammarParser<S> {
	public interface RecognizedComponent<S extends BranchableStream<?, ?>> {
		ExpressionComponent<S> build(int id, Map<String, String> config, String value, List<ExpressionComponent<S>> children);
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
		class PreParsedClass{
			final ExpressionClass<S> clazz;
			final List<ExpressionType<S>> types;
			
			PreParsedClass(String name){
				types=new LinkedList<>();
				clazz=new ExpressionClass<>(name, Collections.unmodifiableList(types));
			}
		}
		Map<String, PreParsedClass> declaredClasses=new HashMap<>();
		class PreParsedType {
			final ExpressionType<S> type;
			final List<ExpressionComponent<S>> children;

			PreParsedType(QommonsConfig config) {
				QommonsConfig[] classConfigs = config.subConfigs("class");
				List<ExpressionClass<S>> classes=new ArrayList<>(classConfigs.length);
				for (int i = 0; i < classConfigs.length; i++)
					classes.add(declaredClasses.computeIfAbsent(classConfigs[i].gV, null)
					classes[i] = classConfigs[i].getValue();
				children = new LinkedList<>();
			}
		}
		Map<String, PreParsedType> declaredTypes = new HashMap<>();
		for (QommonsConfig type : config.subConfigs()) {
			if (!type.getName().equals("expression"))
				throw new IllegalArgumentException("expression elements expected, not " + type.getName());
			String typeName = type.get("name");
			if (typeName == null)
				throw new IllegalArgumentException("expression has no name");
			if (declaredTypes.put(typeName, new PreParsedType(type)) != null)
				throw new IllegalArgumentException("Duplicate expressions named " + typeName);
		}
		// TODO Auto-generated method stub
		return null;
	}

	public static GrammarParser<CharSequenceStream> forText() {
		Map<String, RecognizedComponent<CharSequenceStream>> components = getCommonComponents();
		components.put("literal", (id, config, value, children) -> {
			if (!config.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration points: " + config.keySet());
			else if (!children.isEmpty())
				throw new IllegalArgumentException("Literal cannot have children");
			else if (value == null)
				throw new IllegalArgumentException("Literal declared with no content");
			else
				return new TextLiteralExpression<>(value);
		});
		components.put("pattern", (id, config, value, children) -> {
			String ciStr = config.remove("case-sensitive");
			boolean ci;
			if (ciStr == null || ciStr.equals("false"))
				ci = false;
			else if (ciStr.equals("true"))
				ci = true;
			else
				throw new IllegalArgumentException("Unrecognized value for configuration case-sensitive: " + ciStr);
			if (!config.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration points: " + config.keySet());
			else if (!children.isEmpty())
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
		components.put("sequence", (id, config, value, children) -> {
			if (!config.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration points: " + config.keySet());
			else if (children.isEmpty())
				throw new IllegalArgumentException("Sequence declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("Sequence declared with content");
			else
				return new SequenceExpression<>(id, children);
		});
		components.put("one-of", (id, config, value, children) -> {
			if (!config.isEmpty())
				throw new IllegalArgumentException("Unsupported configuration points: " + config.keySet());
			else if (children.isEmpty())
				throw new IllegalArgumentException("One-of declared with no children");
			else if (value != null)
				throw new IllegalArgumentException("One-of declared with content");
			else
				return new OneOfExpression<>(id, children);
		});
		// TODO
		return components;
	}
}
