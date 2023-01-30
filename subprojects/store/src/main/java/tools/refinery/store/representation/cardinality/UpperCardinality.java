package tools.refinery.store.representation.cardinality;

public sealed interface UpperCardinality extends Comparable<UpperCardinality> permits FiniteUpperCardinality,
		UnboundedUpperCardinality {
	default UpperCardinality min(UpperCardinality other) {
		return this.compareTo(other) <= 0 ? this : other;
	}

	default UpperCardinality max(UpperCardinality other) {
		return this.compareTo(other) >= 0 ? this : other;
	}

	UpperCardinality add(UpperCardinality other);

	UpperCardinality multiply(UpperCardinality other);

	int compareToInt(int value);

	static UpperCardinality of(int upperBound) {
		return UpperCardinalities.valueOf(upperBound);
	}
}