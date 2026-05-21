package org.observe.expresso.qonfig;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.SingletonObservableSet;
import org.observe.config.SyncValueSet;
import org.observe.data.ReflectedEntityMappingScheme;
import org.observe.data.ReflectedEntitySet;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.observe.expresso.qonfig.ObservableModelElement.AbstractConfigModelElement;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.IterableUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.data.csv.CsvEntitySetPersistence;
import org.qommons.data.impl.DataSetMigrationException;
import org.qommons.data.impl.VersionedDataScheme;
import org.qommons.data.values.EntitySetPersistence;
import org.qommons.data.values.GenericEntitySet;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FileUtils;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.NativeFileSource;
import org.qommons.io.TemporalBackupScheme;
import org.qommons.io.TextParseException;

import com.google.common.reflect.TypeToken;

public class EntityDataSet extends AbstractConfigModelElement {
	public static final String ENTITY_DATA_SET = "entity-data-set";

	@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
		qonfigType = ENTITY_DATA_SET,
		interpretation = Interpreted.class,
		instance = EntityDataSet.class)
	public static class Def<M extends EntityDataSet> extends AbstractConfigModelElement.Def<M, ModelValueElement.CompiledSynth<M, ?>> {
		private CompiledExpression theMigrations;
		private final List<EntitySubType.Def> theSubTypes;
		private ModelComponentId theDataSet;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theSubTypes = new ArrayList<>();
		}

		@QonfigAttributeGetter("migrations")
		public CompiledExpression getMigrations() {
			return theMigrations;
		}

		@QonfigChildGetter("sub-types")
		public List<EntitySubType.Def> getSubTypes() {
			return Collections.unmodifiableList(theSubTypes);
		}

		ModelComponentId getDataSet() {
			return theDataSet;
		}

		@Override
		public void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theDataSet = session.getExpressoEnv(getDocument()).getModels().getComponentIfExists(ExpressoConfigV0_1.CONFIG_NAME)
				.getIdentity();
		}

		@Override
		protected DataSourceValueMaker createValueMaker(ExpressoQIS session) throws QonfigInterpretationException {
			theMigrations = getAttributeExpression("migrations", session);
			syncChildren(EntitySubType.Def.class, theSubTypes, session.forChildren("sub-types"));

			return new EntitySetValueMaker(this, getConfigDir(), getConfigName(), getBackup(), //
				QommonsUtils.map(getOldConfigNames(), ocn -> ocn.getOldConfigName(), true), reporting(), theMigrations);
		}

		@Override
		protected Class<CompiledSynth<M, ?>> getValueType() {
			return (Class<ModelValueElement.CompiledSynth<M, ?>>) (Class<?>) ModelValueElement.CompiledSynth.class;
		}

		@Override
		public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class EntitySetValueMaker extends AbstractConfigModelElement.Def.DataSourceValueMaker {
			private final CompiledExpression theMigrations;

			public EntitySetValueMaker(Def<?> configModel, CompiledExpression configDir, String configName, DataBackup.Def<?> backup,
				List<String> oldConfigNames, ErrorReporting reporting, CompiledExpression migrations) {
				super(configModel, configDir, configName, backup, oldConfigNames, reporting);
				theMigrations = migrations;
			}

			@Override
			protected Def<?> getConfigModel() {
				return (Def<?>) super.getConfigModel();
			}

			@Override
			protected InterpretedValueSynth<SettableValue<?>, ?> interpret(
				InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir, DataBackup.Interpreted<?> backup,
				InterpretedExpressoEnv env) throws ExpressoInterpretationException {

				return new Interpreted(configDir, backup, theMigrations.interpret(ModelTypes.Value.STRING, env),
					theMigrations.getFilePosition());
			}

			class Interpreted extends AbstractConfigModelElement.Def.DataSourceValueMaker.Interpreted<ReflectedEntitySet> {
				private final InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theInterpretedMigrations;
				private final LocatedPositionedContent theMigrationsPosition;

				public Interpreted(InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir,
					DataBackup.Interpreted<?> backup, InterpretedValueSynth<SettableValue<?>, SettableValue<String>> migrations,
					LocatedPositionedContent migrationsPosition) {
					super(configDir, backup);
					theInterpretedMigrations = migrations;
					theMigrationsPosition = migrationsPosition;
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<ReflectedEntitySet>> getType() {
					return ModelTypes.Value.forType(ReflectedEntitySet.class);
				}

				@Override
				public EntityDataSet.Interpreted<?> getInterpretedModel() {
					return (EntityDataSet.Interpreted<?>) super.getInterpretedModel();
				}

				@Override
				protected ModelValueInstantiator<SettableValue<ReflectedEntitySet>> instantiate(
					ModelValueInstantiator<SettableValue<BetterFile>> configDir, String configName, DataBackup backup,
					List<String> oldConfigNames, ErrorReporting reporting) throws ModelInstantiationException {
					Set<Class<?>> entityTypes = new LinkedHashSet<>();
					for (ModelValueElement.Interpreted<?, ?, ?> value : getInterpretedModel().getValues()) {
						ModelInstanceType<?, ?> type = value.getType();
						for (TypeToken<?> t : type.getTypeList())
							addType(entityTypes, t.getType());
					}
					return new Instantiator(getInterpretedModel().getAddOnValue(ExNamed.Interpreted.class, ExNamed.Interpreted::getName),
						configDir, configName, backup, oldConfigNames, reporting, entityTypes, //
						theInterpretedMigrations.instantiate(), theMigrationsPosition, //
						QommonsUtils.map(getInterpretedModel().getSubTypes(), EntitySubType.Interpreted::create, false));
				}
			}

			class Instantiator extends AbstractConfigModelElement.Def.DataSourceValueMaker.Instantiator<ReflectedEntitySet> {
				private final String theName;
				private final Set<Class<?>> theEntityTypes;
				private final ModelValueInstantiator<SettableValue<String>> theMigrations;
				private final LocatedPositionedContent theMigrationsPosition;
				private final List<EntitySubType> theSubTypes;

				public Instantiator(String name, ModelValueInstantiator<SettableValue<BetterFile>> interpretedConfigDir, String configName,
					DataBackup backup, List<String> oldConfigNames, ErrorReporting reporting, Set<Class<?>> entityTypes,
					ModelValueInstantiator<SettableValue<String>> migrations, LocatedPositionedContent migrationsPosition,
					List<EntitySubType> subTypes) {
					super(interpretedConfigDir, configName, backup, oldConfigNames, reporting);
					theName = name;
					theEntityTypes = entityTypes;
					theMigrations = migrations;
					theMigrationsPosition = migrationsPosition;
					theSubTypes = subTypes;
				}

				@Override
				protected ReflectedEntitySet create(ModelSetInstance models, BetterFile configDir) throws ModelInstantiationException {
					String migrations = theMigrations.get(models).get();
					BetterFile migrationsFile;
					if (migrations.startsWith("/")) { // Absolute path--assuming in the classpath
						URL resource = getClass().getResource(migrations);
						if (resource == null) {
							ClassLoader ctxCP = Thread.currentThread().getContextClassLoader();
							if (ctxCP != null) {
								resource = ctxCP.getResource(migrations);
								if (resource == null)
									throw new ModelInstantiationException(
										"Could not resolve migrations file in class path at " + migrations,
										theMigrationsPosition.getPosition(0), theMigrationsPosition.length());
							}
						}
						migrationsFile = FileUtils.ofUrl(resource);
					} else if (theMigrationsPosition.getFileLocation() == null) {
						throw new ModelInstantiationException(
							"Cannot resolve migrations file at relative path without a source file path: " + migrations,
							theMigrationsPosition.getPosition(0), theMigrationsPosition.length());
					} else { // Relative to the declaring file
						migrationsFile = new ArchiveEnabledFileSource(new NativeFileSource())//
							.withArchival(new ArchiveEnabledFileSource.ZipCompression())//
							.at(theMigrationsPosition.getFileLocation())//
							.at(migrations);
						if (!migrationsFile.isFile())
							throw new ModelInstantiationException(
								"Could not resolve migrations file '" + migrations + " relative to source file location '"
									+ theMigrationsPosition.getFileLocation() + "'",
									theMigrationsPosition.getPosition(0), theMigrationsPosition.length());
					}
					Set<Class<?>> entityTypes;
					if (theSubTypes.isEmpty())
						entityTypes = theEntityTypes;
					else {
						entityTypes = new LinkedHashSet<>(theEntityTypes);
						for (EntitySubType subType : theSubTypes) {
							for (Class<?> type : subType.iterateTypes())
								addType(entityTypes, type);
						}
					}
					EntitySetPersistence persistence = new CsvEntitySetPersistence();
					ReflectedEntityMappingScheme mapping = new ReflectedEntityMappingScheme();
					try {
						VersionedDataScheme.InitializedDataScheme dataScheme = VersionedDataScheme.init(entityTypes, mapping,
							migrationsFile);
						ReflectedEntityMappingScheme.checkTypeSet(dataScheme.mappedEntityTypes);
						ReflectedEntitySet entitySet = new ReflectedEntitySet(dataScheme.mappedEntityTypes, mapping.getReflectorCache(),
							es -> new StampedLockingStrategy(es, ThreadConstraint.ANY), models.getUntil());

						VersionedDataScheme.LoadedGenericData loadedData;
						try {
							loadedData = dataScheme//
								.load(configDir, null, persistence);
						} catch (CheckedExceptionWrapper wrapper) {
							wrapper.throwIfType(ModelInstantiationException.class);
							throw wrapper;
						}
						GenericEntitySet.copy(loadedData.entityData, entitySet);
						VersionedDataScheme.RollingEntitySetPersistence persister = loadedData.createPersister(persistence);
						TemporalBackupScheme backup;
						if (getBackup() == null)
							backup = new TemporalBackupScheme(); // Default
						else
							backup = getBackup().createBackup();
						persister.withBackup(backup);
						entitySet.onChange(cause -> {
							long dataSetStamp = entitySet.getStamp();
							try {
								persister.save(entitySet, entitySet.getAffectedEntities(), new VersionedDataScheme.PersistenceMonitor() {
									@Override
									public void persistenceSucceeded(long stamp) {
										try (Transaction t = entitySet.lockWrite(false, null)) {
											if (dataSetStamp == entitySet.getStamp())
												entitySet.getAffectedEntities().clear();
										}
									}

									@Override
									public void persistenceAborted(long stamp) {
									}

									@Override
									public void persistenceFailed(long stamp, String error, Throwable exception) {
										getReporting().error("Data set persistence failed: " + error, exception);
									}
								});
							} catch (IOException | RuntimeException e) {
								getReporting().error("Data set persistence failed", e);
							}
						});
						return entitySet;
					} catch (IOException | TextParseException | DataSetMigrationException e) {
						throw new ModelInstantiationException(getReporting().getPosition(), 0, e);
					}
				}
			}

			static void addType(Set<Class<?>> types, Type type) {
				if (type instanceof Class)
					types.add((Class<?>) type);
				else if (type instanceof ParameterizedType) {
					for (Type t : ((ParameterizedType) type).getActualTypeArguments())
						addType(types, t);
				}
			}
		}
	}

	static class Interpreted<M extends EntityDataSet> extends AbstractConfigModelElement.Interpreted<M> {
		private final List<EntitySubType.Interpreted> theSubTypes;

		Interpreted(AbstractConfigModelElement.Def<? super M, ?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theSubTypes = new ArrayList<>();
		}

		@Override
		public Def<? super M> getDefinition() {
			return (Def<? super M>) super.getDefinition();
		}

		public List<EntitySubType.Interpreted> getSubTypes() {
			return Collections.unmodifiableList(theSubTypes);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			syncChildren(getDefinition().getSubTypes(), theSubTypes, def -> def.interpret(this), EntitySubType.Interpreted::update);
		}
	}

	public static class EntitySubType extends ExElement.Abstract {
		public static final String ENTITY_SUB_TYPE = "entity-sub-type";

		@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
			qonfigType = ENTITY_SUB_TYPE,
			interpretation = Interpreted.class,
			instance = EntitySubType.class)
		public static class Def extends ExElement.Def.Abstract<EntitySubType> {
			private CompiledExpression theTypes;
			private VariableType theType;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@QonfigAttributeGetter("types")
			public CompiledExpression getTypes() {
				return theTypes;
			}

			@QonfigAttributeGetter
			public VariableType getType() {
				return theType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theTypes = getAttributeExpression("types", session);
				QonfigValue type = session.getValue().get();
				if (type != null)
					theType = VariableType.parseType(new LocatedPositionedContent.Default(type.fileLocation, type.position));
				else
					theType = null;
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends ExElement.Interpreted.Abstract<EntitySubType> {
			private InterpretedValueSynth<?, ?> theTypes;
			private Class<?> theType;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public InterpretedValueSynth<?, ?> getTypes() {
				return theTypes;
			}

			public Class<?> getType() {
				return theType;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				try {
					theTypes = interpret(getDefinition().getTypes(), ModelTypes.Collection.forType(Class.class));
				} catch (ExpressoInterpretationException e) {
					theTypes = interpret(getDefinition().getTypes(), ModelTypes.Value.forType(Class.class));
				}
				VariableType type = getDefinition().getType();
				if (type != null) {
					TypeToken<?> typeToken = type.getType(getExpressoEnv(type.getContent().getFileLocation()));
					if (!(typeToken.getType() instanceof Class))
						reporting().at(type.getContent()).warn("Type should be a raw type (qualified class name)");
					theType = TypeTokens.getRawType(typeToken);
				} else
					theType = null;
			}

			public EntitySubType create() {
				return new EntitySubType(getIdentity());
			}
		}

		private ModelValueInstantiator<?> theTypesInstantiator;
		private SettableValue<ObservableCollection<Class<?>>> theTypesValue;
		private SettableValue<Class<?>> theTypeValue;
		private ObservableCollection<Class<?>> theTypes;
		private LocatedPositionedContent theType;

		EntitySubType(Object id) {
			super(id);

			initValues();
		}

		private void initValues() {
			theTypesValue = SettableValue.create();
			theTypeValue = SettableValue.create();
		}

		public Iterable<Class<?>> iterateTypes() {
			Class<?> value = theTypeValue.get();
			ObservableCollection<Class<?>> coll = theTypesValue.get();
			if (value == null) {
				if (coll == null)
					return Collections.emptyList();
				else
					return coll;
			} else if (coll == null)
				return Collections.singletonList(value);
			else
				return IterableUtils.concat(Collections.singletonList(value), coll);
		}

		public ObservableCollection<Class<?>> getTypes() {
			if (theTypes == null) {
				theTypes = ObservableCollection.flattenCollections(//
					ObservableCollection.flattenValue(theTypesValue), //
					new SingletonObservableSet<>(theTypeValue))//
					.filter(t -> t == null ? "null" : null)//
					.collectActive(onDestroy());
			}
			return theTypes;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theTypesInstantiator = ExElement.instantiate(myInterpreted.getTypes());
			theTypeValue.set(myInterpreted.getType());
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			Object types = theTypesInstantiator.get(myModels);
			if (types == null) {
				theTypesValue.set(null);
			} else if (types instanceof ObservableCollection)
				theTypesValue.set((ObservableCollection<Class<?>>) types);
			else if (types instanceof SettableValue)
				theTypesValue.set(new SingletonObservableSet<>((SettableValue<Class<?>>) types));
			else
				throw new IllegalStateException("Expected either a collection or a value, not a " + types.getClass().getName());

			return myModels;
		}

		@Override
		public EntitySubType copy(ExElement parent) {
			EntitySubType copy = (EntitySubType) super.copy(parent);

			copy.initValues();

			return copy;
		}
	}

	public static class EDSModelValue<T, MV> extends ModelValueElement.Abstract<MV> {
		public static final String EDS_MODEL_VALUE = "eds-model-value";

		@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
			qonfigType = EDS_MODEL_VALUE,
			interpretation = Interpreted.class,
			instance = EDSModelValue.class)
		public static class Def<M> extends ModelValueElement.Def.Abstract<M, EDSModelValue<?, ?>>
		implements ModelValueElement.CompiledSynth<M, EDSModelValue<?, ?>> {
			private ModelType<M> theType;
			private ModelComponentId theDataSet;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType<M> modelType) {
				super(parent, qonfigType, modelType);
			}

			public ModelType<M> getType() {
				return theType;
			}

			public ModelComponentId getDataSet() {
				return theDataSet;
			}

			@Override
			protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
				CompiledExpressoEnv env = getExpressoEnv(getDocument());
				theType = getModelType(env);
				if (!theType.modelType.isAssignableFrom(ObservableSortedSet.class)
					&& !theType.modelType.isAssignableFrom(SyncValueSet.class))
					reporting().error(EDS_MODEL_VALUE + " is only implemented for list types, not " + theType);
				ObservableModelSet.ModelComponentNode<?> dataSetComponent = env.getModels()
					.getComponentIfExists(ExpressoConfigV0_1.CONFIG_NAME);
				if (dataSetComponent == null)
					reporting().error("Required model component " + ExpressoConfigV0_1.CONFIG_NAME
						+ " not found. This value should be a child of an <" + ENTITY_DATA_SET + "> element");
				ModelType<?> dataSetType;
				try {
					dataSetType = dataSetComponent.getModelType(env);
				} catch (ExpressoCompilationException e) {
					throw new QonfigInterpretationException(reporting().getPosition(), 0, e);
				}
				if (dataSetType != ModelTypes.Value)
					reporting().error("Expected a value for " + ExpressoConfigV0_1.CONFIG_NAME + ", not " + dataSetType
						+ ". This value should be a child of an <" + ENTITY_DATA_SET + "> element.");
				theDataSet = dataSetComponent.getIdentity();
			}

			@Override
			public InterpretedSynth<M, ?, ? extends EDSModelValue<?, ?>> interpretValue(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<T, M, MV extends M> extends ModelValueElement.Interpreted.Abstract<M, MV, EDSModelValue<T, MV>>
		implements ModelValueElement.InterpretedSynth<M, MV, EDSModelValue<T, MV>> {
			private ModelInstanceType<M, MV> theType;

			Interpreted(Def<M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<M> getDefinition() {
				return (Def<M>) super.getDefinition();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected ModelInstanceType<M, MV> getTargetType() {
				return theType;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				ExTyped.Interpreted<T> typed = getAddOn(ExTyped.Interpreted.class);
				TypeToken<T> valueType = typed.getValueType();
				if (!EntityReflector.isEntityType(TypeTokens.getRawType(valueType)))
					throw new ExpressoInterpretationException(
						valueType + " is not a supported entity type for an <" + ENTITY_DATA_SET + ">",
						typed.getDefinition().getValueType().getContent());
				theType = (ModelInstanceType<M, MV>) getDefinition().getType().forTypes(valueType);

				TypeToken<?> dataSourceType = getDefaultEnv().getModels().getComponent(getDefinition().getDataSet()).interpreted().getType()
					.getType(0);
				if (!ReflectedEntitySet.class.isAssignableFrom(TypeTokens.getRawType(dataSourceType)))
					reporting().error("Expected " + ReflectedEntitySet.class.getName() + " value for " + ExpressoConfigV0_1.CONFIG_NAME
						+ ", not " + dataSourceType + ". This value should be a child of an <" + ENTITY_DATA_SET + "> element.");
			}

			@Override
			public EDSModelValue<T, MV> create() throws ModelInstantiationException {
				return new EDSModelValue<>(this);
			}
		}

		private final ModelComponentId theDataSet;
		private final boolean isValueSet;
		private final Class<T> theEntityType;

		EDSModelValue(Interpreted<T, ?, MV> interpreted) throws ModelInstantiationException {
			super(interpreted);
			theDataSet = interpreted.getDefinition().getDataSet();
			isValueSet = interpreted.getType().getModelType().modelType.isAssignableFrom(SyncValueSet.class);
			theEntityType = (Class<T>) TypeTokens.getRawType(interpreted.getType().getType(0));
		}

		@Override
		protected MV evaluate(ModelSetInstance models) throws ModelInstantiationException {
			ReflectedEntitySet dataSet = ((SettableValue<ReflectedEntitySet>) models.get(theDataSet)).get();
			SyncValueSet<?> entities = dataSet.observeEntities(theEntityType);
			if (isValueSet)
				return (MV) entities;
			else
				return (MV) entities.getValues();
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			return value; // Same value, because the data source doesn't change with model copy
		}
	}

	// This is just to shut up a warning
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "sorted-model-value")
	public static class EDSSortedModelValueDef<M> extends EDSModelValue.Def<M> {
		public EDSSortedModelValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType<M> modelType) {
			super(parent, qonfigType, modelType);
		}

		/** @return Stops Expresso from complaining about the sort */
		@QonfigChildGetter("sort")
		public ExElement.Def<?> getSort() {
			return null;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			ExpressoQIS sort = session.children().get("sort").get().peekFirst();
			if (sort != null)
				throw new QonfigInterpretationException("Entity sorting is determined by the data set",
					sort.getElement().getFilePosition());
			super.doPrepare(session);
		}
	}

	private ModelComponentId theDataSet;

	EntityDataSet(Object id) {
		super(id);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		theDataSet = ((Interpreted<?>) interpreted).getDefinition().getDataSet();
	}
}
