package org.observe.expresso.logging;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

public class ExpressoLogging extends ExElementType.AbstractElement {
	public static final String LOGGING = "logging";

	public static final ExElementType.ExElementChild<ExpressoLogger, ExpressoLogger.Interpreted<?>, ExpressoLogger.Def<?>> LOGGER //
	= ExElementType.<ExpressoLogger, ExpressoLogger.Interpreted<?>, ExpressoLogger.Def<?>> child("logger", ExpressoLogger.Def.class,
		ExpressoLogger.Def::interpret, ExpressoLogger.Interpreted::update, ExpressoLogger.Interpreted::create);
	public static final ExElementType.ExElementChild<ExpressoLogCapture, ExpressoLogCapture.Interpreted, ExpressoLogCapture.Def> CAPTURE //
		= ExElementType.<ExpressoLogCapture, ExpressoLogCapture.Interpreted, ExpressoLogCapture.Def> child("capture",
		ExpressoLogCapture.Def.class, ExpressoLogCapture.Def::interpret, ExpressoLogCapture.Interpreted::update,
		ExpressoLogCapture.Interpreted::create);
	public static final ExElementType.TextValue LOG_TO = ExElementType.textValue("log-to");
	public static final ExElementType.TextValue DONT_LOG_TO = ExElementType.textValue("dont-log-to");
	public static final ExElementType.TextValue LOG_EXCLUSIVELY_TO = ExElementType.textValue("log-exclusively-to");
	public static final ExElementType LOGGING_TYPE = ExElementType.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOGGING)//
		.withChild(LOGGER)//
		.withChild(CAPTURE)//
		.withValue(LOG_TO)//
		.withValue(DONT_LOG_TO)//
		.withValue(LOG_EXCLUSIVELY_TO)//
		.build(null);

	public static class Def<L extends ExpressoLogging> extends ExElementType.AbstractElement.Def<L> {
		private Def<?> theParentLogging;
		private final Map<String, ExpressoLogger.Def<?>> theNamedLoggers;
		private final Map<String, ExpressoLogCapture.Def> theNamedCaptures;
		private Set<LogReference> theLogTo;
		private Set<LogReference> theDontLogTo;
		private Set<LogReference> theLogExclusivelyTo;
		private final Set<ExpressoLogCapture.Def> theLoggingTo;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			this(parent, qonfigType, LOGGING_TYPE);
		}

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type) {
			super(parent, qonfigType, type);
			theNamedLoggers = new HashMap<>();
			theNamedCaptures = new HashMap<>();
			theLoggingTo = new LinkedHashSet<>();
		}

		public Set<LogReference> getLogTo() {
			return theLogTo;
		}

		public Set<LogReference> getDontLogTo() {
			return theDontLogTo;
		}

		public Set<LogReference> getLogExclusivelyTo() {
			return theLogExclusivelyTo;
		}

		public Set<ExpressoLogCapture.Def> getLoggingTo() {
			return Collections.unmodifiableSet(theLoggingTo);
		}

		public ExpressoLogger.Def<?> getLogger(LogReference ref) {
			ExpressoLogger.Def<?> logger = theNamedLoggers.get(ref.get(0));
			if (logger != null)
				return logger.getLogger(ref);
			return null;
		}

		public ExpressoLogCapture.Def getCapture(LogReference ref) {
			ExpressoLogCapture.Def capture = theNamedCaptures.get(ref.get(0));
			if (capture != null)
				return capture.getCapture(ref);
			return null;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			ExElement.Def<?> parentEl = getParentElement();
			theParentLogging = null;
			while (parentEl != null && theParentLogging == null) {
				LoggedElement.Def<?> parentLoggedEl = parentEl.getAddOn(LoggedElement.Def.class);
				if (parentLoggedEl != null)
					theParentLogging = parentLoggedEl.getLogging();
			}
			theNamedLoggers.clear();
			theNamedCaptures.clear();
			if (theParentLogging != null) {
				theNamedLoggers.putAll(theParentLogging.theNamedLoggers);
				theNamedCaptures.putAll(theParentLogging.theNamedCaptures);
			}
			for (ExpressoLogger.Def<?> logger : getTypeData().getChildren(LOGGER))
				theNamedLoggers.put(logger.getName(), logger);
			for (ExpressoLogCapture.Def capture : getTypeData().getChildren(CAPTURE))
				theNamedCaptures.put(capture.getName(), capture);

			Function<LogReference, String> checkRef = ref -> {
				if (getCapture(ref) == null)
					return "No such log-capture: " + ref;
				return null;
			};
			theLogTo = LogReference.parse(session.attributes().get(LOG_TO.getAttributeName()).getLocatedContent(), checkRef);
			theDontLogTo = LogReference.parse(session.attributes().get(DONT_LOG_TO.getAttributeName()).getLocatedContent(), checkRef);
			theLogExclusivelyTo = LogReference.parse(session.attributes().get(LOG_EXCLUSIVELY_TO.getAttributeName()).getLocatedContent(),
				checkRef);
			theLoggingTo.clear();
			if (theLogExclusivelyTo != null) {
				for (LogReference ref : theLogExclusivelyTo)
					theLoggingTo.add(getCapture(ref));
				if (theLogTo != null && !theLogTo.isEmpty())
					session.reporting().at(session.attributes().get(LOG_TO.getAttributeName()).getLocatedContent())
					.warn("'log-to' is incompatible with 'log-exclusively-to'");
				if (theDontLogTo != null && !theDontLogTo.isEmpty())
					session.reporting().at(session.attributes().get(DONT_LOG_TO.getAttributeName()).getLocatedContent())
					.warn("'log-to' is incompatible with 'log-exclusively-to'");
			} else {
				if (theParentLogging != null) {
					for (ExpressoLogCapture.Def capture : theParentLogging.getLoggingTo()) {
						if (theDontLogTo == null || !theDontLogTo.contains(capture.getPath()))
							theLoggingTo.add(capture);
					}
				}
				if (theLogTo != null) {
					for (LogReference ref : theLogTo)
						theLoggingTo.add(getCapture(ref));
				}
			}
		}

		@Override
		protected Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<L extends ExpressoLogging> extends ExElementType.AbstractElement.Interpreted<L> {
		protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super L> getDefinition() {
			return (Def<? super L>) super.getDefinition();
		}

		@Override
		protected L create() {
			return (L) new ExpressoLogging(getIdentity());
		}
	}

	private ExpressoLogging theParentLogging;
	private final Map<String, ExpressoLogger> theNamedLoggers;
	private final Map<String, ExpressoLogCapture> theNamedCaptures;
	private final Set<ExpressoLogCapture> theLoggingTo;

	protected ExpressoLogging(Object id) {
		super(id);
		theNamedLoggers = new HashMap<>();
		theNamedCaptures = new HashMap<>();
		theLoggingTo = new LinkedHashSet<>();
	}

	public ExpressoLogger getLogger(LogReference ref) {
		ExpressoLogger logger = theNamedLoggers.get(ref.get(0));
		if (logger != null)
			return logger.getLogger(ref);
		return null;
	}

	public ExpressoLogCapture getCapture(LogReference ref) {
		ExpressoLogCapture capture = theNamedCaptures.get(ref.get(0));
		if (capture != null)
			return capture.getCapture(ref);
		return null;
	}

	public Set<ExpressoLogCapture> getLoggingTo() {
		return Collections.unmodifiableSet(theLoggingTo);
	}

	public boolean isOfInterest(ErrorReporting.IssueSeverity severity) {
		for (ExpressoLogCapture capture : theLoggingTo) {
			if (capture.isOfInterest(severity))
				return true;
		}
		return false;
	}

	public boolean report(ErrorReporting.Issue issue) {
		boolean handled = false;
		for (ExpressoLogCapture capture : theLoggingTo) {
			if (capture.report(issue))
				handled = true;
		}
		return handled;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		ExElement parentEl = getParentElement();
		theParentLogging = null;
		while (parentEl != null && theParentLogging == null) {
			LoggedElement parentLoggedEl = parentEl.getAddOn(LoggedElement.class);
			if (parentLoggedEl != null)
				theParentLogging = parentLoggedEl.getLogging();
		}
		theNamedLoggers.clear();
		theNamedCaptures.clear();
		if (theParentLogging != null) {
			theNamedLoggers.putAll(theParentLogging.theNamedLoggers);
			theNamedCaptures.putAll(theParentLogging.theNamedCaptures);
		}
		for (ExpressoLogger logger : getTypeData().getChildren(LOGGER))
			theNamedLoggers.put(logger.getName(), logger);
		for (ExpressoLogCapture capture : getTypeData().getChildren(CAPTURE))
			theNamedCaptures.put(capture.getName(), capture);

		theLoggingTo.clear();
		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		for (ExpressoLogCapture.Def capture : myInterpreted.getDefinition().getLoggingTo())
			theLoggingTo.add(getCapture(capture.getPath()));
	}
}
