/*
 * SPDX-FileCopyrightText: 2021-2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package tools.refinery.store.query.viatra.internal.matcher;

import tools.refinery.viatra.runtime.matchers.tuple.TupleMask;
import tools.refinery.viatra.runtime.matchers.tuple.Tuples;
import tools.refinery.viatra.runtime.rete.index.IterableIndexer;
import tools.refinery.viatra.runtime.rete.matcher.RetePatternMatcher;
import tools.refinery.store.map.Cursor;
import tools.refinery.store.query.dnf.FunctionalQuery;
import tools.refinery.store.query.viatra.internal.ViatraModelQueryAdapterImpl;
import tools.refinery.store.tuple.Tuple;

/**
 * Directly access the tuples inside a VIATRA pattern matcher.<p>
 * This class neglects calling
 * {@link tools.refinery.viatra.runtime.matchers.context.IQueryRuntimeContext#wrapTuple(org.eclipse.viatra.query.runtime.matchers.tuple.Tuple)}
 * and
 * {@link tools.refinery.viatra.runtime.matchers.context.IQueryRuntimeContext#unwrapTuple(org.eclipse.viatra.query.runtime.matchers.tuple.Tuple)},
 * because {@link tools.refinery.store.query.viatra.internal.context.RelationalRuntimeContext} provides a trivial
 * implementation for these methods.
 * Using this class with any other runtime context may lead to undefined behavior.
 */
public class FunctionalViatraMatcher<T> extends AbstractViatraMatcher<T> {
	private final TupleMask emptyMask;
	private final TupleMask omitOutputMask;
	private final IterableIndexer omitOutputIndexer;

	public FunctionalViatraMatcher(ViatraModelQueryAdapterImpl adapter, FunctionalQuery<T> query,
								   RawPatternMatcher rawPatternMatcher) {
		super(adapter, query, rawPatternMatcher);
		int arity = query.arity();
		int arityWithOutput = arity + 1;
		emptyMask = TupleMask.empty(arityWithOutput);
		omitOutputMask = TupleMask.omit(arity, arityWithOutput);
		if (backend instanceof RetePatternMatcher reteBackend) {
			var maybeIterableOmitOutputIndexer = reteBackend.getInternalIndexer(omitOutputMask);
			if (maybeIterableOmitOutputIndexer instanceof IterableIndexer iterableOmitOutputIndexer) {
				omitOutputIndexer = iterableOmitOutputIndexer;
			} else {
				omitOutputIndexer = null;
			}
		} else {
			omitOutputIndexer = null;
		}
	}

	@Override
	public T get(Tuple parameters) {
		var tuple = MatcherUtils.toViatraTuple(parameters);
		if (omitOutputIndexer == null) {
			return MatcherUtils.getSingleValue(backend.getAllMatches(omitOutputMask, tuple).iterator());
		} else {
			return MatcherUtils.getSingleValue(omitOutputIndexer.get(tuple));
		}
	}

	@Override
	public Cursor<Tuple, T> getAll() {
		if (omitOutputIndexer == null) {
			var allMatches = backend.getAllMatches(emptyMask, Tuples.staticArityFlatTupleOf());
			return new UnsafeFunctionalCursor<>(allMatches.iterator());
		}
		return new FunctionalCursor<>(omitOutputIndexer);
	}

	@Override
	public int size() {
		if (omitOutputIndexer == null) {
			return backend.countMatches(emptyMask, Tuples.staticArityFlatTupleOf());
		}
		return omitOutputIndexer.getBucketCount();
	}

	@Override
	public void update(tools.refinery.viatra.runtime.matchers.tuple.Tuple updateElement, boolean isInsertion) {
		var key = MatcherUtils.keyToRefineryTuple(updateElement);
		var value = MatcherUtils.<T>getValue(updateElement);
		if (isInsertion) {
			notifyChange(key, null, value);
		} else {
			notifyChange(key, value, null);
		}
	}
}
