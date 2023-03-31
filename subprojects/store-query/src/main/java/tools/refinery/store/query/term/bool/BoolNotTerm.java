package tools.refinery.store.query.term.bool;

import tools.refinery.store.query.substitution.Substitution;
import tools.refinery.store.query.term.Term;
import tools.refinery.store.query.term.UnaryTerm;

public class BoolNotTerm extends UnaryTerm<Boolean, Boolean> {
	protected BoolNotTerm(Term<Boolean> body) {
		super(body);
	}

	@Override
	public Class<Boolean> getType() {
		return Boolean.class;
	}

	@Override
	public Class<Boolean> getBodyType() {
		return getType();
	}

	@Override
	protected Term<Boolean> doSubstitute(Substitution substitution, Term<Boolean> substitutedBody) {
		return new BoolNotTerm(substitutedBody);
	}

	@Override
	protected Boolean doEvaluate(Boolean bodyValue) {
		return !bodyValue;
	}

	@Override
	public String toString() {
		return "!(%s)".formatted(getBody());
	}
}