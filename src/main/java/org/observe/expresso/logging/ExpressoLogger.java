package org.observe.expresso.logging;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableSortedMap;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExNamed;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.Named;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

public abstract class ExpressoLogger extends ExElementType.AbstractElement implements Named {
	public static final String LOGGER = "logger";

	public static final ExElementType.ExElementModelValue<ObservableSortedMap<?, ?>, ObservableSortedMap<LogSeverity, Instant>> SEVERITY//
	= ExElementType.modelAttributeValue("severity", ModelTypes.SortedMap.forType(LogSeverity.class, Instant.class));
	public static final ExElementType.ExElementModelValue<Observable<?>, Observable<LogSeverity>> LOG = ExElementType
		.modelAttributeValue("log", ModelTypes.Event.forType(LogSeverity.class));
	public static final ExElementType.ExElementChild<ExpressoLogger, Interpreted<?>, Def<?>> CHILD = ExElementType
		.<ExpressoLogger, Interpreted<?>, Def<?>> child("child", Def.class, Def::interpret, Interpreted::update, Interpreted::create);
	public static final ExElementType LOGGER_TYPE = ExElementType.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOGGER)//
		.withModelValue(SEVERITY)//
		.withModelValue(LOG)//
		.withChild(CHILD)//
		.build(null);

	public static abstract class Def<L extends ExpressoLogger> extends ExElementType.AbstractElement.Def<L> implements Named {
		private String theName;
		private LogReference thePath;
		private final Map<String, Def<?>> theChildren;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ExElementType type) {
			super(parent, qonfigType, type);
			theChildren = new HashMap<>();
		}

		@Override
		public String getName() {
			return theName;
		}

		public LogReference getPath() {
			return thePath;
		}

		public ExpressoLogger.Def<?> getLogger(LogReference ref) {
			if (ref.size() == 1)
				return this;
			return getChild(ref, 1);
		}

		private Def<?> getChild(LogReference ref, int index) {
			Def<?> child = theChildren.get(ref.get(index));
			if (child == null || index + 1 == ref.size())
				return child;
			else
				return child.getChild(ref, index);
		}

		public List<Def<?>> getChildren() {
			return getTypeData().getChildren(CHILD);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = getAddOn(ExNamed.Def.class).getName();
			if (getParentElement() instanceof Def)
				thePath = ((Def<?>) getParentElement()).getPath().append(theName);
			theChildren.clear();
			for (Def<?> child : getChildren())
				theChildren.put(child.getName(), child);
		}

		@Override
		public abstract Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<L extends ExpressoLogger> extends ExElementType.AbstractElement.Interpreted<L> {
		protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super L> getDefinition() {
			return (Def<? super L>) super.getDefinition();
		}

		@Override
		public abstract L create();
	}

	private String theName;
	private SimpleObservable<LogSeverity> theLogEvent;
	private ObservableSortedMap<LogSeverity, Instant> theSeverity;
	private final Map<String, ExpressoLogger> theChildren;

	protected ExpressoLogger(Object id) {
		super(id);
		theLogEvent = new SimpleObservable<>();
		theSeverity = ObservableSortedMap.create(LogSeverity::compareTo);
		theChildren = new HashMap<>();
	}

	@Override
	public String getName() {
		return theName;
	}

	public ExpressoLogger getLogger(LogReference ref) {
		if (ref.size() == 1)
			return this;
		return getChild(ref, 1);
	}

	private ExpressoLogger getChild(LogReference ref, int index) {
		ExpressoLogger child = theChildren.get(ref.get(index));
		if (child == null || index + 1 == ref.size())
			return child;
		else
			return child.getChild(ref, index);
	}

	public List<ExpressoLogger> getChildren() {
		return getTypeData().getChildren(CHILD);
	}

	protected void log(ErrorReporting.Issue issue, ExpressoLogCapture capture) {
		doLog(issue, capture);
		LogSeverity severity = LogElement.getSeverity(issue.severity);
		theLogEvent.onNext(severity);
		theSeverity.put(severity, LogElement.getLogTime(issue));
	}

	protected abstract void doLog(ErrorReporting.Issue issue, ExpressoLogCapture capture);

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		theName = ((Interpreted<?>) interpreted).getDefinition().getName();
		theChildren.clear();
		for (ExpressoLogger child : getChildren())
			theChildren.put(child.getName(), child);
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		getTypeData().satisfyModelValue(SEVERITY, () -> theSeverity);
		getTypeData().satisfyModelValue(LOG, () -> theLogEvent);

		return myModels;
	}
}
