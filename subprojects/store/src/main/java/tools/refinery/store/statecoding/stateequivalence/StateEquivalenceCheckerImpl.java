/*
 * SPDX-FileCopyrightText: 2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.store.statecoding.stateequivalence;

import org.eclipse.collections.api.map.primitive.IntIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import tools.refinery.store.model.Interpretation;
import tools.refinery.store.statecoding.Morphism;
import tools.refinery.store.statecoding.ObjectCode;
import tools.refinery.store.statecoding.StateEquivalenceChecker;
import tools.refinery.store.tuple.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StateEquivalenceCheckerImpl implements StateEquivalenceChecker {
	public static final int LIMIT = 1000;

	@Override
	public EquivalenceResult constructMorphism(List<? extends Interpretation<?>> interpretations1,
												ObjectCode code1,
												List<? extends Interpretation<?>> interpretations2,
												ObjectCode code2) {
		if (code1.getSize() != code2.getSize()) {
			return EquivalenceResult.DIFFERENT;
		}

		IntIntHashMap object2PermutationGroup = new IntIntHashMap();
		List<List<IntIntMap>> permutationsGroups = new ArrayList<>();

		final EquivalenceResult permutations = constructPermutationNavigation(indexByHash(code1), indexByHash(code2),
				object2PermutationGroup, permutationsGroups);

		if (permutations == EquivalenceResult.DIFFERENT) {
			return EquivalenceResult.DIFFERENT;
		}

		boolean hasNext;
		PermutationMorphism morphism = new PermutationMorphism(object2PermutationGroup, permutationsGroups);
		int tried = 0;
		do {
			if (testMorphism(interpretations1, interpretations2, morphism)) {
				return permutations;
			}

			if(tried >= LIMIT) {
				return EquivalenceResult.UNKNOWN;
			}

			hasNext = morphism.next();
			tried++;
		} while (hasNext);

		return EquivalenceResult.DIFFERENT;
	}

	private LongObjectHashMap<IntHashSet> indexByHash(ObjectCode code) {
		LongObjectHashMap<IntHashSet> result = new LongObjectHashMap<>();
		for (int o = 0; o < code.getSize(); o++) {
			long hash = code.get(o);
			var equivalenceClass = result.get(hash);
			if (equivalenceClass == null) {
				equivalenceClass = new IntHashSet();
				result.put(hash, equivalenceClass);
			}
			equivalenceClass.add(o);
		}
		return result;
	}

	private EquivalenceResult constructPermutationNavigation(LongObjectHashMap<IntHashSet> map1,
												   LongObjectHashMap<IntHashSet> map2,
												   IntIntHashMap emptyMapToListOfOptions,
												   List<List<IntIntMap>> emptyListOfOptions) {
		if (map1.size() != map2.size()) {
			return EquivalenceResult.DIFFERENT;
		}

		var iterator = map1.keySet().longIterator();

		boolean allComplete = true;

		while (iterator.hasNext()) {
			long hash = iterator.next();
			var set1 = map1.get(hash);
			var set2 = map2.get(hash);
			if (set2 == null) {
				return EquivalenceResult.DIFFERENT;
			}

			var pairing = NodePairing.constructNodePairing(set1, set2);
			if (pairing == null) {
				return EquivalenceResult.DIFFERENT;
			}

			allComplete &= pairing.isComplete();

			final int optionIndex = emptyListOfOptions.size();
			set1.forEach(key -> emptyMapToListOfOptions.put(key, optionIndex));
			emptyListOfOptions.add(pairing.permutations());
		}
		if(allComplete) {
			return EquivalenceResult.ISOMORPHIC;
		} else {
			return EquivalenceResult.UNKNOWN;
		}
	}

	private boolean testMorphism(List<? extends Interpretation<?>> s, List<? extends Interpretation<?>> t, Morphism m) {
		for (int interpretationIndex = 0; interpretationIndex < s.size(); interpretationIndex++) {
			var sI = s.get(interpretationIndex);
			var tI = t.get(interpretationIndex);

			var cursor = sI.getAll();
			while (cursor.move()) {
				final Tuple sTuple = cursor.getKey();
				final Object sValue = cursor.getValue();

				final Tuple tTuple = apply(sTuple, m);
				final Object tValue = tI.get(tTuple);

				if (!Objects.equals(sValue, tValue)) {
					return false;
				}
			}
		}
		return true;
	}

	private Tuple apply(Tuple t, Morphism m) {
		final int arity = t.getSize();
		if (arity == 0) {
			return Tuple.of();
		} else if (arity == 1) {
			return Tuple.of(m.get(t.get(0)));
		} else if (arity == 2) {
			return Tuple.of(m.get(t.get(0)), m.get(t.get(1)));
		} else {
			int[] newTupleIndices = new int[arity];
			for (int i = 0; i < arity; i++) {
				newTupleIndices[i] = m.get(t.get(i));
			}
			return Tuple.of(newTupleIndices);
		}
	}
}