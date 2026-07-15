package org.observe.expresso.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.io.BetterFile;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

public abstract class LogFile extends ExpressoLogger {
	public static final String LOG_FILE = "log-file";

	public static final ExElementType.ValueExpression<BetterFile, ?> DIRECTORY = ExElementType.valueExpression("directory",
		BetterFile.class, null);
	public static final ExElementType LOG_FILE_TYPE = ExElementType.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOG_FILE)//
		.withValue(DIRECTORY)//
		.build(ExpressoLogger.LOGGER_TYPE);

	public interface FileOutputType extends AutoCloseable {
		@Override
		public void close() throws IOException;
	}

	public interface LogFileWriter extends AutoCloseable {
		public void log(ErrorReporting.Issue issue, ExpressoLogCapture capture) throws IOException;

		@Override
		public void close() throws IOException;
	}

	public interface LogElementWriter<O> {
		public void logElement(ErrorReporting.Issue issue, ExpressoLogCapture capture, O output) throws IOException;
	}

	public static abstract class Def<F extends LogFile> extends ExpressoLogger.Def<F> {
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			this(parent, qonfigType, LOG_FILE_TYPE);
		}

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type) {
			super(parent, qonfigType, type);
		}

		@Override
		public abstract Interpreted<? extends F> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<F extends LogFile> extends ExpressoLogger.Interpreted<F> {
		protected Interpreted(Def<? super F> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super F> getDefinition() {
			return (Def<? super F>) super.getDefinition();
		}
	}

	private LogFileWriter theWriter;

	protected LogFile(Object id) {
		super(id);
	}

	@Override
	protected void doLog(ErrorReporting.Issue issue, ExpressoLogCapture capture) {
		todo = todo;
	}

	public static abstract class LogFileType<O extends FileOutputType, P, F extends LogFile> extends ExAddOn.Abstract<F> {
		public static abstract class Def<O extends FileOutputType, P, F extends LogFile>
		extends ExAddOn.Def.Abstract<F, LogFileType<O, P, F>> {
			protected Def(QonfigAddOn type, ExElement.Def<? extends F> element) {
				super(type, element);
			}

			@Override
			public abstract <E2 extends F> Interpreted<O, P, F> interpret(ExElement.Interpreted<E2> element);
		}

		public static abstract class Interpreted<O extends FileOutputType, P, F extends LogFile>
		extends ExAddOn.Interpreted.Abstract<F, LogFileType<O, P, F>> {
			protected Interpreted(Def<O, P, F> definition, ExElement.Interpreted<? extends F> element) {
				super(definition, element);
			}

			public abstract TypeToken<P> getPrintType();

			@Override
			public abstract LogFileType<O, P, F> create(ExElement element);
		}

		private final List<LogElementWriter<? super O>> theElementWriters;
		private O theOutput;

		protected LogFileType(ExElement element) {
			super(element);
			theElementWriters = new ArrayList<>();
		}

		@Override
		public Class<? extends Interpreted<O, P, F>> getInterpretationType() {
			return (Class<? extends Interpreted<O, P, F>>) Interpreted.class;
		}

		public LogFileWriter writeLogFile(OutputStream out) throws IOException {
			O output = createOutput(out);
			return new LogFileWriter() {
				@Override
				public void log(ErrorReporting.Issue issue, ExpressoLogCapture capture) throws IOException {
					for (LogElementWriter<? super O> element : theElementWriters)
						element.logElement(issue, capture, output);
					endLog(output);
				}

				@Override
				public void close() throws IOException {
					output.close();
					out.close();
				}
			};
		}

		protected abstract O createOutput(OutputStream out) throws IOException;

		protected abstract void endLog(O out) throws IOException;
	}

	public static abstract class LogFileElement<O extends FileOutputType> extends ExElementType.AbstractAddOn<LogElement<?>> {
		public static final String LOG_FILE_ELEMENT = "log-file-element";

		public static final ExElementType.ValueExpression<?, ?> PRINT = ExElementType.valueExpression("print", interp -> {
			return interp.getParentElement().getAddOn(LogFileType.Interpreted.class).getPrintType();
		}, null);
		public static final ExElementType LOG_FILE_ELEMENT_TYPE = ExElementType
			.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOG_FILE_ELEMENT)//
			.withValue(PRINT)//
			.build(null);

		public static abstract class Def extends ExElementType.AbstractAddOn.Def<LogElement<?>, LogFileElement<?>> {
			protected Def(QonfigAddOn type, ExElement.Def<? extends LogElement<?>> element) {
				this(type, element, LOG_FILE_ELEMENT_TYPE);
			}

			protected Def(QonfigAddOn type, org.observe.expresso.qonfig.ExElement.Def<? extends LogElement<?>> element,
				ExElementType exType) {
				super(type, element, exType.createDefData());
			}

			@Override
			public abstract <E2 extends LogElement<?>> Interpreted<?> interpret(ExElement.Interpreted<E2> element);
		}

		public static abstract class Interpreted<O extends FileOutputType>
		extends ExElementType.AbstractAddOn.Interpreted<LogElement<?>, LogFileElement<O>> {
			protected Interpreted(Def definition, ExElement.Interpreted<? extends LogElement<?>> element) {
				super(definition, element);
			}

			@Override
			public Class<LogFileElement<O>> getInstanceType() {
				return (Class<LogFileElement<O>>) (Class<?>) LogFileElement.class;
			}

			@Override
			public abstract LogFileElement<O> create(ExElement element);
		}

		protected LogFileElement(LogElement<?> element) {
			super(element);
		}

		public abstract LogElementWriter<O> getElementWriter();
	}
}
