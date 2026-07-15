package org.observe.expresso.logging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn.ActionIfSatisfied;
import org.observe.expresso.qonfig.ExNamed;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.Named;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;
import org.qommons.io.ErrorReporting.Issue;
import org.qommons.io.ErrorReporting.IssueSeverity;

public class ExpressoLogCapture extends ExElementType.AbstractElement implements Named {
	public static final String LOG_CAPTURE = "log-capture";

	public static final ExElementType.ValueExpression<Boolean, ?> IF = ExElementType.valueExpression("if", boolean.class, null);
	public static final ExElementType.ExElementChild<ExpressoLogger, ExpressoLogger.Interpreted<?>, ExpressoLogger.Def<?>> LOGGER = ExElementType.<ExpressoLogger, ExpressoLogger.Interpreted<?>, ExpressoLogger.Def<?>> child(
		"logger", ExpressoLogger.Def.class, ExpressoLogger.Def::interpret, ExpressoLogger.Interpreted::update,
		ExpressoLogger.Interpreted::create);
	public static final ExElementType.ExElementChild<ExpressoLogCapture, Interpreted, Def> CHILD = ExElementType
		.<ExpressoLogCapture, Interpreted, Def> child("child", Def.class, Def::interpret, Interpreted::update, Interpreted::create);
	public static final ExElementType LOG_CAPTURE_TYPE = ExElementType
		.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, LOG_CAPTURE)//
		.withValue(IF)//
		.withChild(LOGGER)//
		.withChild(CHILD)//
		.build(null);

	public static class Def extends ExElementType.AbstractElement.Def<ExpressoLogCapture> implements Named {
		private String theName;
		private LogReference thePath;
		private final Map<String, Def> theChildren;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			this(parent, qonfigType, LOG_CAPTURE_TYPE);
		}

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

		public ExpressoLogCapture.Def getCapture(LogReference ref) {
			if (ref.size() == 1)
				return this;
			return getChild(ref, 1);
		}

		private Def getChild(LogReference ref, int index) {
			Def child = theChildren.get(ref.get(index));
			if (child == null || index + 1 == ref.size())
				return child;
			else
				return child.getChild(ref, index);
		}

		public List<Def> getChildren() {
			return getTypeData().getChildren(CHILD);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = getAddOn(ExNamed.Def.class).getName();
			if (getParentElement() instanceof Def)
				thePath = ((Def) getParentElement()).getPath().append(theName);
			theChildren.clear();
			for (Def child : getChildren())
				theChildren.put(child.getName(), child);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElementType.AbstractElement.Interpreted<ExpressoLogCapture> {
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public ExpressoLogCapture create() {
			return new ExpressoLogCapture(getIdentity());
		}
	}

	private String theName;
	private LogReference thePath;
	private final Map<String, ExpressoLogCapture> theChildren;
	private LogIssueFilter theFilter;

	protected ExpressoLogCapture(Object id) {
		super(id);
		theChildren = new HashMap<>();
	}

	@Override
	public String getName() {
		return theName;
	}

	public LogReference getPath() {
		return thePath;
	}

	public ExpressoLogCapture getCapture(LogReference ref) {
		if (ref.size() == 1)
			return this;
		return getChild(ref, 1);
	}

	private ExpressoLogCapture getChild(LogReference ref, int index) {
		ExpressoLogCapture child = theChildren.get(ref.get(index));
		if (child == null || index + 1 == ref.size())
			return child;
		else
			return child.getChild(ref, index);
	}

	public List<ExpressoLogCapture> getChildren() {
		return getTypeData().getChildren(CHILD);
	}

	public List<ExpressoLogger> getLoggers() {
		return getTypeData().getChildren(LOGGER);
	}

	public boolean isOfInterest(ErrorReporting.IssueSeverity severity) {
		return theFilter != null && theFilter.isOfInterest(severity);
	}

	public boolean report(ErrorReporting.Issue issue) {
		if (theFilter == null || !theFilter.isOfInterest(issue))
			return false;
		for (ExpressoLogger logger : getLoggers())
			logger.log(issue, this);
		return true;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		theName = ((Interpreted) interpreted).getDefinition().getName();
		thePath = ((Interpreted) interpreted).getDefinition().getPath();
		theChildren.clear();
		for (ExpressoLogCapture child : getChildren())
			theChildren.put(child.getName(), child);
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ModelSetInstance copy = myModels.copy().build();
		List<Consumer<ErrorReporting.IssueSeverity>> severityPopulate = new ArrayList<>();
		List<Consumer<ErrorReporting.Issue>> issuePopulate = new ArrayList<>();
		for (LogElement<?> element : getAddOn(ConfigurableLoggingElement.class).getElements()) {
			SettableValue<Object> elValue = SettableValue.create();
			ExWithElementModel.satisfyElementValue(element.getAs(), copy, elValue, ActionIfSatisfied.Replace);
			if (element instanceof LogElement.SeveritySensitive) {
				BiFunction<ErrorReporting.IssueSeverity, ExpressoLogCapture, ?> sensitivityValue = ((LogElement.SeveritySensitive<?>) element)
					.getSeverityValue();
				severityPopulate.add(s -> elValue.set(sensitivityValue.apply(s, this)));
			} else
				severityPopulate.add(s -> elValue.set(null));
			BiFunction<ErrorReporting.Issue, ExpressoLogCapture, ?> issueValue = element.getLogValue();
			issuePopulate.add(i -> elValue.set(issueValue.apply(i, this)));
		}
		SettableValue<Boolean> ifV = getTypeData().getValue(IF);
		theFilter = new LogIssueFilter(severityPopulate, issuePopulate, ifV);

		return myModels;
	}

	static class LogIssueFilter {
		private final List<Consumer<ErrorReporting.IssueSeverity>> theSeverityPopulate;
		private final List<Consumer<ErrorReporting.Issue>> theIssuePopulate;
		private final Supplier<Boolean> theValue;

		LogIssueFilter(List<Consumer<IssueSeverity>> severityPopulate, List<Consumer<Issue>> issuePopulate, Supplier<Boolean> value) {
			theSeverityPopulate = severityPopulate;
			theIssuePopulate = issuePopulate;
			theValue = value;
		}

		boolean isOfInterest(ErrorReporting.IssueSeverity severity) {
			for (Consumer<ErrorReporting.IssueSeverity> populate : theSeverityPopulate)
				populate.accept(severity);
			return theValue.get();
		}

		boolean isOfInterest(ErrorReporting.Issue issue) {
			for (Consumer<ErrorReporting.Issue> populate : theIssuePopulate)
				populate.accept(issue);
			return theValue.get();
		}
	}
}
