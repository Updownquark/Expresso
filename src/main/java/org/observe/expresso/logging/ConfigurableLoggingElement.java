package org.observe.expresso.logging;

import java.util.List;
import java.util.Set;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementType;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ConfigurableLoggingElement extends ExElementType.AbstractAddOn<ExElement> {
	public static final String CONFIGURABLE_LOGGING_ELEMENT = "configurable-logging-element";

	public static final ExElementType.ExElementChild<LogElement<?>, LogElement.Interpreted<?, ?>, LogElement.Def<?>> ELEMENT//
	= ExElementType.<LogElement<?>, LogElement.Interpreted<?, ?>, LogElement.Def<?>> child("element", LogElement.Def.class,
		LogElement.Def::interpret, LogElement.Interpreted::update, LogElement.Interpreted::create);
	public static final ExElementType CLE_TYPE = ExElementType
		.build(ExpressoLoggingV0_1.NAME, ExpressoLoggingV0_1.VERSION, CONFIGURABLE_LOGGING_ELEMENT)//
		.withChild(ELEMENT)//
		.build(null);

	public static class Def extends ExElementType.AbstractAddOn.Def<ExElement, ConfigurableLoggingElement> {
		protected Def(QonfigAddOn qonfigType, ExElement.Def<? extends ExElement> element) {
			this(qonfigType, element, CLE_TYPE);
		}

		protected Def(QonfigAddOn qonfigType, ExElement.Def<? extends ExElement> element, ExElementType type) {
			super(qonfigType, element, type.createDefData());
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return QommonsUtils.unmodifiableDistinctCopy((Class<? extends ExAddOn.Def<?, ?>>) ExModelAugmentation.Def.class);
		}

		public List<LogElement.Def<?>> getElements() {
			return getTypeData().getChildren(ELEMENT);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);

			// Satisfy element "as" types
			ExWithElementModel.Def elModels = element.getAddOn(ExWithElementModel.Def.class);
			for (LogElement.Def<?> logEl : getElements()) {
				Object elId = logEl.getIdentity();
				elModels.satisfyElementSingleValueType(logEl.getModelValue(LogElement.AS), ModelTypes.Value, interp -> {
					Interpreted myInterpreted = (Interpreted) interp;
					for (LogElement.Interpreted<?, ?> interpLogEl : myInterpreted.getElements()) {
						if (interpLogEl.getIdentity().equals(elId))
							return interpLogEl.getLogValueType();
					}
					throw new IllegalStateException("No interpretation for log element " + elId);
				});
			}
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExElementType.AbstractAddOn.Interpreted<ExElement, ConfigurableLoggingElement> {
		protected Interpreted(ExElementType.AbstractAddOn.Def<? super ExElement, ? super ConfigurableLoggingElement> definition,
			ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		public List<LogElement.Interpreted<?, ?>> getElements() {
			return getTypeData().getChildren(ELEMENT);
		}

		@Override
		public Class<ConfigurableLoggingElement> getInstanceType() {
			return ConfigurableLoggingElement.class;
		}

		@Override
		public ConfigurableLoggingElement create(ExElement element) {
			return new ConfigurableLoggingElement(element);
		}
	}

	protected ConfigurableLoggingElement(ExElement element) {
		super(element);
	}

	public List<LogElement<?>> getElements() {
		return getTypeData().getChildren(ELEMENT);
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<ExElement, ?>> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		return models;
	}
}
