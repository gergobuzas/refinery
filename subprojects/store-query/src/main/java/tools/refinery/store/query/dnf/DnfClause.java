package tools.refinery.store.query.dnf;

import tools.refinery.store.query.equality.LiteralEqualityHelper;
import tools.refinery.store.query.literal.Literal;
import tools.refinery.store.query.term.Variable;

import java.util.List;
import java.util.Set;

public record DnfClause(Set<Variable> boundVariables, List<Literal> literals) {
	public boolean equalsWithSubstitution(LiteralEqualityHelper helper, DnfClause other) {
		int size = literals.size();
		if (size != other.literals.size()) {
			return false;
		}
		for (int i = 0; i < size; i++) {
			if (!literals.get(i).equalsWithSubstitution(helper, other.literals.get(i))) {
				return false;
			}
		}
		return true;
	}
}