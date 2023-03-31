package tools.refinery.store.query.literal;

import tools.refinery.store.query.term.Term;

public final class Literals {
	private Literals() {
		throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
	}

	public static <T extends CanNegate<T>> T not(CanNegate<T> literal) {
		return literal.negate();
	}

	public static AssumeLiteral assume(Term<Boolean> term) {
		return new AssumeLiteral(term);
	}
}