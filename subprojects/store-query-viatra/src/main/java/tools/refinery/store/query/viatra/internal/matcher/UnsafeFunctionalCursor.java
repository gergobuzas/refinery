package tools.refinery.store.query.viatra.internal.matcher;

import org.eclipse.viatra.query.runtime.matchers.tuple.ITuple;
import tools.refinery.store.map.Cursor;
import tools.refinery.store.tuple.TupleLike;

import java.util.Iterator;

/**
 * Cursor for a functional result set that iterates over a stream of raw matches and doesn't check whether the
 * functional dependency of the output on the inputs is obeyed.
 * @param <T> The output type.
 */
class UnsafeFunctionalCursor<T> implements Cursor<TupleLike, T> {
	private final Iterator<? extends ITuple> tuplesIterator;
	private boolean terminated;
	private TupleLike key;
	private T value;

	public UnsafeFunctionalCursor(Iterator<? extends ITuple> tuplesIterator) {
		this.tuplesIterator = tuplesIterator;
	}

	@Override
	public TupleLike getKey() {
		return key;
	}

	@Override
	public T getValue() {
		return value;
	}

	@Override
	public boolean isTerminated() {
		return terminated;
	}

	@Override
	public boolean move() {
		if (!terminated && tuplesIterator.hasNext()) {
			var match = tuplesIterator.next();
			key = new OmitOutputViatraTupleLike(match);
			@SuppressWarnings("unchecked")
			var typedValue = (T) match.get(match.getSize() - 1);
			value = typedValue;
			return true;
		}
		terminated = true;
		return false;
	}
}