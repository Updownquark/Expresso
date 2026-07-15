package org.observe.expresso.logging;

import java.util.List;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExElementType.DefTypeData;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

@ExElementTraceable(toolkit = ExpressoLoggingV0_1.LOGGING, qonfigType = LoggedElement.LOGGED_ELEMENT)
public class LoggedElement extends ExElementType.AbstractAddOn<ExElement> {
	public static final String LOGGED_ELEMENT = "logged-element";

	public static final ExElementType.ExElementChild<ExpressoLogging, ExpressoLogging.Interpreted<?>, ExpressoLogging.Def<?>> LOGGING//
	= ExElementType.<ExpressoLogging, ExpressoLogging.Interpreted<?>, ExpressoLogging.Def<?>> child(ExpressoLogging.LOGGING,
		ExpressoLogging.Def.class, ExpressoLogging.Def::interpret, ExpressoLogging.Interpreted::update,
		ExpressoLogging.Interpreted::create);
	public static final ExElementType LOGGED_ELEMENT_TYPE = ExElementType
		.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOGGED_ELEMENT)//
		.withChild(LOGGING)//
		.build(null);

	public static class Def<L extends LoggedElement> extends ExElementType.AbstractAddOn.Def<ExElement, L> {
		private final LoggedReporting theReporting;

		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			this(type, element, LOGGED_ELEMENT_TYPE.createDefData());
		}

		protected Def(QonfigAddOn type, ExElement.Def<?> element, DefTypeData typeData) {
			super(type, element, typeData);
			theReporting = new LoggedReporting();
		}

		public ExpressoLogging.Def<?> getLogging() {
			return getTypeData().getChild(LOGGING);
		}

		@Override
		public void preUpdate(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			theReporting.setUnlogged(session.reporting());
			session.getWrapped().setReporting(theReporting);
			super.preUpdate(session, element);
		}

		@Override
		public <E2 extends ExElement> Interpreted<? extends L> interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted<>(this, element);
		}
	}

	public static class Interpreted<L extends LoggedElement> extends ExElementType.AbstractAddOn.Interpreted<ExElement, L> {
		protected Interpreted(ExElementType.AbstractAddOn.Def<? super ExElement, ? super L> definition,
			ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Class<L> getInstanceType() {
			return (Class<L>) LoggedElement.class;
		}

		@Override
		public L create(ExElement element) {
			return (L) new LoggedElement(element);
		}
	}

	private final LoggedReporting theReporting;

	public LoggedElement(ExElement element) {
		super(element);
		theReporting = (LoggedReporting) element.reporting();
	}

	@Override
	public Class<? extends Interpreted<?>> getInterpretationType() {
		return (Class<? extends Interpreted<?>>) Interpreted.class;
	}

	public ExpressoLogging getLogging() {
		return getTypeData().getChild(LOGGING);
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		theReporting.setLogging(getTypeData().getChild(LOGGING));

		return models;
	}

	private static class LoggedReporting implements ErrorReporting {
		private ExpressoLogging theLogging;
		private ErrorReporting theUnlogged;
		private List<ExpressoLogCapture> theCaptures;

		ErrorReporting getUnlogged() {
			return theUnlogged;
		}

		LoggedReporting setUnlogged(ErrorReporting unlogged) {
			if (unlogged instanceof LoggedReporting)
				theUnlogged = ((LoggedReporting) unlogged).getUnlogged();
			else
				theUnlogged = unlogged;
			return this;
		}

		LoggedReporting setLogging(ExpressoLogging logging) {
			theLogging = logging;
			return this;
		}

		@Override
		public LocatedPositionedContent getFileLocation() {
			return theUnlogged.getFileLocation();
		}

		@Override
		public void ignoreClass(String className) {
			theUnlogged.ignoreClass(className);
		}

		@Override
		public StackTraceElement getCodeLocation() {
			return theUnlogged.getCodeLocation();
		}

		@Override
		public ErrorReporting at(LocatedPositionedContent position) {
			return new LoggedReporting().setUnlogged(theUnlogged.at(position)).setLogging(theLogging);
		}

		@Override
		public boolean isOfInterest(IssueSeverity severity) {
			return (theLogging != null && theLogging.isOfInterest(severity)) || (theUnlogged != null && theUnlogged.isOfInterest(severity));
		}

		@Override
		public ErrorReporting report(Issue issue) {
			if ((theLogging == null || !theLogging.report(issue)) && theUnlogged != null)
				theUnlogged.report(issue);
			return this;
		}
	}
}
