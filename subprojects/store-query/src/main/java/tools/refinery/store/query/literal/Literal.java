package tools.refinery.store.query.literal;

import tools.refinery.store.query.term.Variable;
import tools.refinery.store.query.equality.LiteralEqualityHelper;
import tools.refinery.store.query.substitution.Substitution;

import java.util.Set;

public interface Literal {
	Set<Variable> getBoundVariables();

	Literal substitute(Substitution substitution);

	default LiteralReduction getReduction() {
		return LiteralReduction.NOT_REDUCIBLE;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean equalsWithSubstitution(LiteralEqualityHelper helper, Literal other);
}