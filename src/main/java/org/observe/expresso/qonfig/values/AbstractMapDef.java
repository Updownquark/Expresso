package org.observe.expresso.qonfig.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.assoc.ObservableMap;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Abstract ExElement definition for the Expresso &lt;int-map> element.
 *
 * @param <M> The sub-type of {@link ObservableMap} that this element creates
 */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "int-map",
	interpretation = AbstractMapDef.Interpreted.class,
	instance = AbstractMapDef.Instantiator.class)
public abstract class AbstractMapDef<M extends ObservableMap<?, ?>> extends
ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M>> implements ModelValueElement.CompiledSynth<M, ModelValueElement<M>> {
	private final List<MapEntry> theEntries;

	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 * @param modelType The map model type for the value
	 */
	protected AbstractMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
		super(parent, qonfigType, modelType);
		theEntries = new ArrayList<>();
	}

	/** @return Entries defined to initialize this map */
	@QonfigChildGetter("entry")
	public List<MapEntry> getEntries() {
		return Collections.unmodifiableList(theEntries);
	}

	@Override
	protected boolean useWrapperType() {
		return true;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		if (session.isInstance("int-map") != null) {
			ExpressoQIS intMapSession = session.asElement("int-map");
			syncChildren(MapEntry.class, theEntries, intMapSession.forChildren("entry"));
		}
	}

	@Override
	protected void doPrepare(ExpressoQIS session) {
	}

	@Override
	public AbstractMapDef.Interpreted<?, ?, M> interpretValue(ExElement.Interpreted<?> parent) {
		return (AbstractMapDef.Interpreted<?, ?, M>) interpret2(parent);
	}

	/**
	 * @param parent The parent element for the interpreted map
	 * @return The interpreted map element
	 */
	protected abstract AbstractMapDef.Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent);

	/**
	 * {@link AbstractMapDef} interpretation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <M> The sub-type of {@link ObservableMap} to create
	 */
	public static abstract class Interpreted<K, V, M extends ObservableMap<K, V>>
	extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M>>
	implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M>> {
		private final List<MapEntry.Interpreted<K, V>> theEntries;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this map element
		 */
		protected Interpreted(AbstractMapDef<?> definition, ExElement.Interpreted<?> parent) {
			super((AbstractMapDef<M>) definition, parent);
			theEntries = new ArrayList<>();
		}

		@Override
		public AbstractMapDef<M> getDefinition() {
			return (AbstractMapDef<M>) super.getDefinition();
		}

		/** @return Entries defined to initialize this map */
		public List<MapEntry.Interpreted<K, V>> getEntries() {
			return Collections.unmodifiableList(theEntries);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
		}

		/** @return The key type of the map */
		protected TypeToken<K> getKeyType() {
			return TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
		}

		/** @return The value type of the map */
		protected TypeToken<V> getValueType() {
			return TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			ModelInstanceType<M, M> type = getType();
			TypeToken<K> keyType = (TypeToken<K>) type.getType(0);
			TypeToken<V> valueType = (TypeToken<V>) type.getType(1);
			try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
				syncChildren(getDefinition().getEntries(), theEntries, d -> (MapEntry.Interpreted<K, V>) d.interpret(this),
					entry -> entry.update(keyType, valueType));
			}
		}

		/**
		 * Instantiate's this map's initial content
		 *
		 * @return The instantiators for this map's initial content
		 * @throws ModelInstantiationException If any of this map's initial content could not be instantiated
		 */
		protected List<MapEntry.MapPopulator<K, V>> instantiateEntries() throws ModelInstantiationException {
			return QommonsUtils.filterMapE(theEntries, null, e -> e.create());
		}
	}

	/**
	 * {@link AbstractMapDef} instantiator
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <M> The sub-type of {@link ObservableMap} to create
	 */
	public static abstract class Instantiator<K, V, M extends ObservableMap<K, V>> extends ModelValueElement.Abstract<M> {
		private final List<MapEntry.MapPopulator<K, V>> theEntries;
		private final String theAlias;

		/**
		 * @param interpreted The interpretation to instantiate
		 * @param elements The initial content for the map
		 * @throws ModelInstantiationException If any model values fail to instantiate
		 */
		protected Instantiator(AbstractMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> elements)
			throws ModelInstantiationException {
			super(interpreted);
			theEntries = elements;
			theAlias = getModelPath() + ":" + interpreted.toString();
		}

		/** @return Entries defined to initialize this map */
		public List<MapEntry.MapPopulator<K, V>> getEntries() {
			return Collections.unmodifiableList(theEntries);
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			for (MapEntry.MapPopulator<?, ?> entry : theEntries)
				entry.instantiate();
		}

		@Override
		public M evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			ObservableMap.Builder<K, V, ?> builder = create(models);
			if (getModelPath() != null)
				builder.withDescription(getModelPath());
			M map = (M) builder.buildMap();
			for (MapEntry.MapPopulator<K, V> entry : theEntries)
				entry.populateMap(map, models);
			return (M) map.alias(theAlias);
		}

		@Override
		public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			// Configured entries are merely initialized, not slaved, and the map may have been modified
			// since it was created. There's no sense to making a re-initialized copy here.
			return value;
		}

		/**
		 * Creates a builder for the map
		 *
		 * @param models The model instances to use to instantiate the map
		 * @return The map builder
		 * @throws ModelInstantiationException If the map cannot be instantiated
		 */
		protected abstract ObservableMap.Builder<K, V, ?> create(ModelSetInstance models)
			throws ModelInstantiationException;
	}
}