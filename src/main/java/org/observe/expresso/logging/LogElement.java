package org.observe.expresso.logging;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.io.ErrorReporting;
import org.qommons.io.ErrorReporting.IssueProperty;
import org.qommons.threading.Singleton;

import com.google.common.reflect.TypeToken;

public abstract class LogElement<V> extends ExElementType.AbstractElement {
	public static final String LOG_ELEMENT = "log-element";
	public static final IssueProperty<Long> LOG_ID = new IssueProperty<>("log-id");
	public static final IssueProperty<Instant> LOG_TIME = new IssueProperty<>("log-time");
	public static final IssueProperty<Instant> LOG_RUNTIME_ID = new IssueProperty<>("log-runtime-id");
	public static final IssueProperty<LogSeverity> LOG_SEVERITY = new IssueProperty<>("log-severity");
	public static final IssueProperty<String> LOG_MESSAGE = new IssueProperty<>("log-message");
	public static final IssueProperty<String> LOG_EXCEPTION = new IssueProperty<>("log-exception");
	public static final IssueProperty<List<StackTraceElement>> LOG_STACK_TRACE = new IssueProperty<>("log-stack-trace");

	private static final AtomicLong LOG_ID_GEN = new AtomicLong(0);
	private static final Singleton<Instant> LOG_RUNTIME_ID_VALUE = Singleton.of(Instant::now);
	private static Instant initLogTime() {
		return Instant.ofEpochMilli(System.currentTimeMillis());
	}
	private static final LogSeverity[] SEVERITIES = LogSeverity.values();
	public static LogSeverity getSeverity(ErrorReporting.IssueSeverity severity) {
		// Maintain these enums together
		return SEVERITIES[severity.ordinal()];
	}

	public static Instant getLogTime(ErrorReporting.Issue issue) {
		return issue.getProperty(LOG_TIME, LogElement::initLogTime);
	}

	public static final ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<?>> AS //
	= (ExElementType.ExElementModelValue<SettableValue<?>, SettableValue<?>>) (Object) ExElementType
	.<Object, Interpreted<Object, ?>> modelAttributeValue("as", Interpreted::getLogValueType);
	public static final ExElementType LOG_ELEMENT_TYPE = ExElementType
		.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOG_ELEMENT)//
		.withModelValue(AS)//
		.build(null);

	public static abstract class Def<E extends LogElement<?>> extends ExElementType.AbstractElement.Def<E> {
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type) {
			super(parent, qonfigType, type);
		}

		@Override
		public abstract Interpreted<?, ? extends E> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<V, E extends LogElement<V>> extends ExElementType.AbstractElement.Interpreted<E> {
		protected Interpreted(Def<? super E> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super E> getDefinition() {
			return (Def<? super E>) super.getDefinition();
		}

		public abstract TypeToken<V> getLogValueType();

		@Override
		protected abstract E create();
	}

	protected LogElement(Object id) {
		super(id);
	}

	public ModelComponentId getAs() {
		return getTypeData().getValueId(AS);
	}

	protected abstract BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> getLogValue();

	public interface SeveritySensitive<V> {
		BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> getSeverityValue();
	}

	public static void populateStandardLogElements(QonfigInterpreterCore.Builder interpreter) {
		populateStandardLogElement(interpreter, "log-id", TypeTokens.get().of(long.class),
			(issue, capture) -> issue.getProperty(LOG_ID, LOG_ID_GEN::incrementAndGet), null);
		populateStandardLogElement(interpreter, "log-time", TypeTokens.get().of(Instant.class),
			(issue, capture) -> issue.getProperty(LOG_TIME, LogElement::initLogTime), (__, capture) -> initLogTime());
		populateStandardLogElement(interpreter, "log-runtime-id", TypeTokens.get().of(Instant.class),
			(issue, capture) -> issue.getProperty(LOG_RUNTIME_ID, LOG_RUNTIME_ID_VALUE::get), (__, capture) -> LOG_RUNTIME_ID_VALUE.get());
		populateStandardLogElement(interpreter, "log-severity", TypeTokens.get().of(LogSeverity.class),
			(issue, capture) -> getSeverity(issue.severity), (sev, capture) -> getSeverity(sev));
		populateStandardLogElement(interpreter, "log-capture-name", TypeTokens.get().STRING, (issue, capture) -> capture.getName(),
			(sev, capture) -> capture.getName());
		populateStandardLogElement(interpreter, "log-capture-path", TypeTokens.get().keyFor(List.class).parameterized(String.class),
			(issue, capture) -> capture.getPath(), (sev, capture) -> capture.getPath());
		populateStandardLogElement(interpreter, "log-message", TypeTokens.get().STRING, (issue, capture) -> issue.message, null);
		populateStandardLogElement(interpreter, "log-exception", TypeTokens.get().STRING, (issue, capture) -> {
			if (issue.cause == null)
				return null;
			String msg = issue.cause.getMessage();
			return msg == null ? issue.cause.toString() : msg;
		}, null);
		populateStandardLogElement(interpreter, "log-stack-trace",
			TypeTokens.get().keyFor(List.class).parameterized(StackTraceElement.class), (issue, capture) -> {
				if (issue.cause == null)
					return null;
				return QommonsUtils.unmodifiableCopy(issue.cause.getStackTrace());
			}, null);
		populateStandardLogElement(interpreter, "log-source-file-name", TypeTokens.get().STRING, (issue, capture) -> {
			if (issue.fileLocation == null)
				return null;
			String path = issue.fileLocation.getFileName();
			int lastSlash = path.lastIndexOf('/');
			return lastSlash < 0 ? path : path.substring(lastSlash + 1);
		}, null);
		populateStandardLogElement(interpreter, "log-source-file-path", TypeTokens.get().STRING, (issue, capture) -> {
			if (issue.fileLocation == null)
				return null;
			return issue.fileLocation.getFileName();
		}, null);
		populateStandardLogElement(interpreter, "log-source-file-line", TypeTokens.get().of(int.class), (issue, capture) -> {
			if (issue.fileLocation == null)
				return null;
			return issue.fileLocation.getLineNumber() + 1;
		}, null);
		populateStandardLogElement(interpreter, "log-source-file-column", TypeTokens.get().of(int.class), (issue, capture) -> {
			if (issue.fileLocation == null)
				return null;
			return issue.fileLocation.getCharNumber() + 1;
		}, null);
		populateStandardLogElement(interpreter, "log-source-file-position", TypeTokens.get().of(int.class), (issue, capture) -> {
			if (issue.fileLocation == null)
				return null;
			return issue.fileLocation.getPosition() + 1;
		}, null);
	}

	private static <V> void populateStandardLogElement(QonfigInterpreterCore.Builder interpreter, String name, TypeToken<V> valueType,
		BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> value,
		BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> severityValue) {
		if (severityValue == null) {
			interpreter.<Simple.Def<V, Simple<V>>> createWith(name, (Class<Simple.Def<V, Simple<V>>>) (Class<?>) Simple.Def.class,
				ExElement.creator((parent, qonfigType) -> new Simple.Def<>(parent, qonfigType, valueType, value)));
		} else {
			interpreter.<Simple.SSDef<V, Simple.SimpleSeveritySensitive<V>>> createWith(name,
				(Class<Simple.SSDef<V, Simple.SimpleSeveritySensitive<V>>>) (Class<?>) Simple.Def.class,
				ExElement.creator((parent, qonfigType) -> new Simple.SSDef<>(parent, qonfigType, valueType, value, severityValue)));
		}
	}

	public static class Simple<V> extends LogElement<V> {
		public static class Def<V, E extends Simple<V>> extends LogElement.Def<E> {
			private final TypeToken<V> theValueType;
			private final BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> theValue;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, TypeToken<V> valueType,
				BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> value) {
				this(parent, qonfigType, LOG_ELEMENT_TYPE, valueType, value);
			}

			protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type, TypeToken<V> valueType,
				BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> value) {
				super(parent, qonfigType, type);
				theValueType = valueType;
				theValue = value;
			}

			public TypeToken<V> getValueType() {
				return theValueType;
			}

			public BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> getValue() {
				return theValue;
			}

			@Override
			public Interpreted<?, ? extends E> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<V, E extends Simple<V>> extends LogElement.Interpreted<V, E> {
			protected Interpreted(Def<V, ? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<V, ? super E> getDefinition() {
				return (Def<V, ? super E>) super.getDefinition();
			}

			@Override
			public TypeToken<V> getLogValueType() {
				return getDefinition().getValueType();
			}

			@Override
			protected E create() {
				return (E) new Simple<>(getIdentity());
			}
		}

		private BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> theValue;

		protected Simple(Object id) {
			super(id);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			theValue = ((Interpreted<V, ?>) interpreted).getDefinition().getValue();
		}

		@Override
		protected BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> getLogValue() {
			return theValue;
		}

		public static class SimpleSeveritySensitive<V> extends Simple<V> implements SeveritySensitive<V> {
			private BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> theSeverityValue;

			protected SimpleSeveritySensitive(Object id) {
				super(id);
			}

			@Override
			protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
				super.doUpdate(interpreted);

				theSeverityValue = ((SSInterpreted<V, ?>) interpreted).getDefinition().getSeverityValue();
			}

			@Override
			public BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> getSeverityValue() {
				return theSeverityValue;
			}
		}

		public static class SSDef<V, E extends SimpleSeveritySensitive<V>> extends Def<V, E> {
			private BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> theSeverityValue;

			public SSDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, TypeToken<V> valueType,
				BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> value,
				BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> severityValue) {
				this(parent, qonfigType, LOG_ELEMENT_TYPE, valueType, value, severityValue);
			}

			protected SSDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type, TypeToken<V> valueType,
				BiFunction<ErrorReporting.Issue, ExpressoLogCapture, V> value,
				BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> severityValue) {
				super(parent, qonfigType, type, valueType, value);
				theSeverityValue = severityValue;
			}

			public BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, V> getSeverityValue() {
				return theSeverityValue;
			}
		}

		public static class SSInterpreted<V, E extends SimpleSeveritySensitive<V>> extends Interpreted<V, E> {
			protected SSInterpreted(SSDef<V, ? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SSDef<V, ? super E> getDefinition() {
				return (SSDef<V, ? super E>) super.getDefinition();
			}

			@Override
			protected E create() {
				return (E) new SimpleSeveritySensitive<>(getIdentity());
			}
		}
	}
}
