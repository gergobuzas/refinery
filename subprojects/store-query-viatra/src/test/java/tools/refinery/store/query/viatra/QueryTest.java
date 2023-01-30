package tools.refinery.store.query.viatra;

import org.junit.jupiter.api.Test;
import tools.refinery.store.model.ModelStore;
import tools.refinery.store.query.DNF;
import tools.refinery.store.query.ModelQuery;
import tools.refinery.store.query.Variable;
import tools.refinery.store.query.atom.*;
import tools.refinery.store.query.view.FilteredRelationView;
import tools.refinery.store.query.view.KeyOnlyRelationView;
import tools.refinery.store.representation.Symbol;
import tools.refinery.store.representation.TruthValue;
import tools.refinery.store.tuple.Tuple;
import tools.refinery.store.tuple.TupleLike;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryTest {
	@Test
	void typeConstraintTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var asset = new Symbol<>("Asset", 1, Boolean.class, false);
		var personView = new KeyOnlyRelationView<>(person);

		var p1 = new Variable("p1");
		var predicate = DNF.builder("TypeConstraint")
				.parameters(p1)
				.clause(new RelationViewAtom(personView, p1))
				.build();

		var store = ModelStore.builder()
				.symbols(person, asset)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var assetInterpretation = model.getInterpretation(asset);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);

		assetInterpretation.put(Tuple.of(1), true);
		assetInterpretation.put(Tuple.of(2), true);

		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0), Tuple.of(1)));
	}

	@Test
	void relationConstraintTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("RelationConstraint")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		assertEquals(0, predicateResultSet.countResults());

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 0), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		assertEquals(0, predicateResultSet.countResults());

		queryEngine.flushChanges();
		assertEquals(3, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0, 1), Tuple.of(1, 0), Tuple.of(1, 2)));
	}

	@Test
	void andTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("RelationConstraint")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2),
						new RelationViewAtom(friendMustView, p2, p1)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		assertEquals(0, predicateResultSet.countResults());

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(0, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(0, predicateResultSet.countResults());

		friendInterpretation.put(Tuple.of(1, 0), TruthValue.TRUE);
		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0, 1), Tuple.of(1, 0)));

		friendInterpretation.put(Tuple.of(2, 0), TruthValue.TRUE);
		queryEngine.flushChanges();
		assertEquals(4, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0, 1), Tuple.of(1, 0), Tuple.of(0, 2),
				Tuple.of(2, 0)));
	}

	@Test
	void existTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("RelationConstraint")
				.parameters(p1)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 0), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		assertEquals(0, predicateResultSet.countResults());

		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0), Tuple.of(1)));
	}

	@Test
	void orTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var animal = new Symbol<>("Animal", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var animalView = new KeyOnlyRelationView<>(animal);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("Or")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.clause(
						new RelationViewAtom(animalView, p1),
						new RelationViewAtom(animalView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, animal, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var animalInterpretation = model.getInterpretation(animal);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);

		animalInterpretation.put(Tuple.of(2), true);
		animalInterpretation.put(Tuple.of(3), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(0, 2), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(2, 3), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(3, 0), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0, 1), Tuple.of(2, 3)));
	}

	@Test
	void equalityTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var personView = new KeyOnlyRelationView<>(person);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("Equality")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new EquivalenceAtom(p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		queryEngine.flushChanges();
		assertEquals(3, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0, 0), Tuple.of(1, 1), Tuple.of(2, 2)));
	}

	@Test
	void inequalityTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var p3 = new Variable("p3");
		var predicate = DNF.builder("Inequality")
				.parameters(p1, p2, p3)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p3),
						new RelationViewAtom(friendMustView, p2, p3),
						new EquivalenceAtom(false, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 2), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
		compareMatchSets(predicateResultSet.allResults(), Set.of(Tuple.of(0, 1, 2), Tuple.of(1, 0, 2)));
	}

	@Test
	void patternCallTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var friendPredicate = DNF.builder("RelationConstraint")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var p3 = new Variable("p3");
		var p4 = new Variable("p4");
		var predicate = DNF.builder("PositivePatternCall")
				.parameters(p3, p4)
				.clause(
						new RelationViewAtom(personView, p3),
						new RelationViewAtom(personView, p4),
						new DNFCallAtom(friendPredicate, p3, p4)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 0), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(3, predicateResultSet.countResults());
	}

	@Test
	void negativeRelationViewTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("NegativePatternCall")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(false, friendMustView, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 0), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(6, predicateResultSet.countResults());
	}

	@Test
	void negativePatternCallTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var friendPredicate = DNF.builder("RelationConstraint")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var p3 = new Variable("p3");
		var p4 = new Variable("p4");
		var predicate = DNF.builder("NegativePatternCall")
				.parameters(p3, p4)
				.clause(
						new RelationViewAtom(personView, p3),
						new RelationViewAtom(personView, p4),
						new DNFCallAtom(false, friendPredicate, p3, p4)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 0), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(6, predicateResultSet.countResults());
	}

	@Test
	void negativeRelationViewWithQuantificationTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");

		var predicate = DNF.builder("Count")
				.parameters(p1)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(false, friendMustView, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(0, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
	}

	@Test
	void negativeWithQuantificationTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");

		var called = DNF.builder("Called")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var predicate = DNF.builder("Count")
				.parameters(p1)
				.clause(
						new RelationViewAtom(personView, p1),
						new DNFCallAtom(false, called, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(0, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(2, predicateResultSet.countResults());
	}

	@Test
	void transitiveRelationViewTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var predicate = DNF.builder("TransitivePatternCall")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(CallPolarity.TRANSITIVE, friendMustView, p1, p2)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(3, predicateResultSet.countResults());
	}

	@Test
	void transitivePatternCallTest() {
		var person = new Symbol<>("Person", 1, Boolean.class, false);
		var friend = new Symbol<>("friend", 2, TruthValue.class, TruthValue.FALSE);
		var personView = new KeyOnlyRelationView<>(person);
		var friendMustView = new FilteredRelationView<>(friend, "must", TruthValue::must);

		var p1 = new Variable("p1");
		var p2 = new Variable("p2");
		var friendPredicate = DNF.builder("RelationConstraint")
				.parameters(p1, p2)
				.clause(
						new RelationViewAtom(personView, p1),
						new RelationViewAtom(personView, p2),
						new RelationViewAtom(friendMustView, p1, p2)
				)
				.build();

		var p3 = new Variable("p3");
		var p4 = new Variable("p4");
		var predicate = DNF.builder("TransitivePatternCall")
				.parameters(p3, p4)
				.clause(
						new RelationViewAtom(personView, p3),
						new RelationViewAtom(personView, p4),
						new DNFCallAtom(CallPolarity.TRANSITIVE, friendPredicate, p3, p4)
				)
				.build();

		var store = ModelStore.builder()
				.symbols(person, friend)
				.with(ViatraModelQuery.ADAPTER)
				.queries(predicate)
				.build();

		var model = store.createModel();
		var personInterpretation = model.getInterpretation(person);
		var friendInterpretation = model.getInterpretation(friend);
		var queryEngine = model.getAdapter(ModelQuery.ADAPTER);
		var predicateResultSet = queryEngine.getResultSet(predicate);

		personInterpretation.put(Tuple.of(0), true);
		personInterpretation.put(Tuple.of(1), true);
		personInterpretation.put(Tuple.of(2), true);

		friendInterpretation.put(Tuple.of(0, 1), TruthValue.TRUE);
		friendInterpretation.put(Tuple.of(1, 2), TruthValue.TRUE);

		queryEngine.flushChanges();
		assertEquals(3, predicateResultSet.countResults());
	}

	static void compareMatchSets(Stream<TupleLike> matchSet, Set<Tuple> expected) {
		Set<Tuple> translatedMatchSet = new HashSet<>();
		var iterator = matchSet.iterator();
		while (iterator.hasNext()) {
			var element = iterator.next();
			translatedMatchSet.add(element.toTuple());
		}
		assertEquals(expected, translatedMatchSet);
	}
}