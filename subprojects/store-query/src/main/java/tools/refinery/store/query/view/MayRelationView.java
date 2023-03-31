package tools.refinery.store.query.view;

import tools.refinery.store.representation.Symbol;
import tools.refinery.store.representation.TruthValue;
import tools.refinery.store.tuple.Tuple;

public class MayRelationView extends TuplePreservingRelationView<TruthValue> {
	public MayRelationView(Symbol<TruthValue> symbol) {
		super(symbol, "may");
	}

	@Override
	public boolean filter(Tuple key, TruthValue value) {
		return value.may();
	}
}