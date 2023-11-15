/*
 * SPDX-FileCopyrightText: 2021-2023 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/*
 * generated by Xtext 2.25.0
 */
package tools.refinery.language.validation;

import com.google.inject.Inject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.validation.Check;
import tools.refinery.language.model.problem.*;
import tools.refinery.language.utils.ProblemUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * This class contains custom validation rules.
 * <p>
 * See
 * <a href="https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation">...</a>
 */
public class ProblemValidator extends AbstractProblemValidator {
	private static final String ISSUE_PREFIX = "tools.refinery.language.validation.ProblemValidator.";

	public static final String SINGLETON_VARIABLE_ISSUE = ISSUE_PREFIX + "SINGLETON_VARIABLE";

	public static final String NODE_CONSTANT_ISSUE = ISSUE_PREFIX + "NODE_CONSTANT_ISSUE";

	public static final String DUPLICATE_NAME_ISSUE = ISSUE_PREFIX + "DUPLICATE_NAME";

	public static final String INVALID_MULTIPLICITY_ISSUE = ISSUE_PREFIX + "INVALID_MULTIPLICITY";

	public static final String ZERO_MULTIPLICITY_ISSUE = ISSUE_PREFIX + "ZERO_MULTIPLICITY";

	public static final String MISSING_OPPOSITE_ISSUE = ISSUE_PREFIX + "MISSING_OPPOSITE";

	public static final String INVALID_OPPOSITE_ISSUE = ISSUE_PREFIX + "INVALID_OPPOSITE";

	@Inject
	private ReferenceCounter referenceCounter;

	@Check
	public void checkSingletonVariable(VariableOrNodeExpr expr) {
		var variableOrNode = expr.getVariableOrNode();
		if (variableOrNode instanceof Variable variable && ProblemUtil.isImplicitVariable(variable)
				&& !ProblemUtil.isSingletonVariable(variable)) {
			var problem = EcoreUtil2.getContainerOfType(variable, Problem.class);
			if (problem != null && referenceCounter.countReferences(problem, variable) <= 1) {
				var name = variable.getName();
				var message = ("Variable '%s' has only a single reference. " +
						"Add another reference or mark is as a singleton variable: '_%s'").formatted(name, name);
				warning(message, expr, ProblemPackage.Literals.VARIABLE_OR_NODE_EXPR__VARIABLE_OR_NODE,
						INSIGNIFICANT_INDEX, SINGLETON_VARIABLE_ISSUE);
			}
		}
	}

	@Check
	public void checkNodeConstants(VariableOrNodeExpr expr) {
		var variableOrNode = expr.getVariableOrNode();
		if (variableOrNode instanceof Node node && !ProblemUtil.isIndividualNode(node)) {
			var name = node.getName();
			var message = ("Only individuals can be referenced in predicates. " +
					"Mark '%s' as individual with the declaration 'indiv %s.'").formatted(name, name);
			error(message, expr, ProblemPackage.Literals.VARIABLE_OR_NODE_EXPR__VARIABLE_OR_NODE,
					INSIGNIFICANT_INDEX, NODE_CONSTANT_ISSUE);
		}
	}

	@Check
	public void checkUniqueDeclarations(Problem problem) {
		var relations = new ArrayList<Relation>();
		var individuals = new ArrayList<Node>();
		for (var statement : problem.getStatements()) {
			if (statement instanceof Relation relation) {
				relations.add(relation);
			} else if (statement instanceof IndividualDeclaration individualDeclaration) {
				individuals.addAll(individualDeclaration.getNodes());
			}
		}
		checkUniqueSimpleNames(relations);
		checkUniqueSimpleNames(individuals);
	}

	@Check
	public void checkUniqueFeatures(ClassDeclaration classDeclaration) {
		checkUniqueSimpleNames(classDeclaration.getFeatureDeclarations());
	}

	@Check
	public void checkUniqueLiterals(EnumDeclaration enumDeclaration) {
		checkUniqueSimpleNames(enumDeclaration.getLiterals());
	}

	protected void checkUniqueSimpleNames(Iterable<? extends NamedElement> namedElements) {
		var names = new LinkedHashMap<String, Set<NamedElement>>();
		for (var namedElement : namedElements) {
			var name = namedElement.getName();
			var objectsWithName = names.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
			objectsWithName.add(namedElement);
		}
		for (var entry : names.entrySet()) {
			var objectsWithName = entry.getValue();
			if (objectsWithName.size() <= 1) {
				continue;
			}
			var name = entry.getKey();
			var message = "Duplicate name '%s'.".formatted(name);
			for (var namedElement : objectsWithName) {
				acceptError(message, namedElement, ProblemPackage.Literals.NAMED_ELEMENT__NAME, 0,
						DUPLICATE_NAME_ISSUE);
			}
		}
	}

	@Check
	public void checkRangeMultiplicity(RangeMultiplicity rangeMultiplicity) {
		int lower = rangeMultiplicity.getLowerBound();
		int upper = rangeMultiplicity.getUpperBound();
		if (upper >= 0 && lower > upper) {
			var message = "Multiplicity range [%d..%d] is inconsistent.";
			acceptError(message, rangeMultiplicity, null, 0, INVALID_MULTIPLICITY_ISSUE);
		}
	}

	@Check
	public void checkReferenceMultiplicity(ReferenceDeclaration referenceDeclaration) {
		var multiplicity = referenceDeclaration.getMultiplicity();
		if (multiplicity == null) {
			return;
		}
		if (ProblemUtil.isContainerReference(referenceDeclaration) && (
				!(multiplicity instanceof RangeMultiplicity rangeMultiplicity) ||
						rangeMultiplicity.getLowerBound() != 0 ||
						rangeMultiplicity.getUpperBound() != 1)) {
			var message = "The only allowed multiplicity for container references is [0..1]";
			acceptError(message, multiplicity, null, 0, INVALID_MULTIPLICITY_ISSUE);
		}
		if ((multiplicity instanceof ExactMultiplicity exactMultiplicity &&
				exactMultiplicity.getExactValue() == 0) ||
				(multiplicity instanceof RangeMultiplicity rangeMultiplicity &&
						rangeMultiplicity.getLowerBound() == 0 &&
						rangeMultiplicity.getUpperBound() == 0)) {
			var message = "The multiplicity constraint does not allow any reference links";
			acceptWarning(message, multiplicity, null, 0, ZERO_MULTIPLICITY_ISSUE);
		}
	}

	@Check
	public void checkOpposite(ReferenceDeclaration referenceDeclaration) {
		var opposite = referenceDeclaration.getOpposite();
		if (opposite == null || opposite.eIsProxy()) {
			return;
		}
		var oppositeOfOpposite = opposite.getOpposite();
		if (oppositeOfOpposite == null) {
			acceptError("Reference '%s' does not declare '%s' as an opposite."
							.formatted(opposite.getName(), referenceDeclaration.getName()),
					referenceDeclaration, ProblemPackage.Literals.REFERENCE_DECLARATION__OPPOSITE, 0,
					INVALID_OPPOSITE_ISSUE);
			var oppositeResource = opposite.eResource();
			if (oppositeResource != null && oppositeResource.equals(referenceDeclaration.eResource())) {
				acceptError("Missing opposite '%s' for reference '%s'."
								.formatted(referenceDeclaration.getName(), opposite.getName()),
						opposite, ProblemPackage.Literals.NAMED_ELEMENT__NAME, 0, MISSING_OPPOSITE_ISSUE);
			}
			return;
		}
        if (!referenceDeclaration.equals(oppositeOfOpposite)) {
            var messageBuilder = new StringBuilder()
                    .append("Expected reference '")
                    .append(opposite.getName())
                    .append("' to have opposite '")
                    .append(referenceDeclaration.getName())
                    .append("'");
            var oppositeOfOppositeName = oppositeOfOpposite.getName();
            if (oppositeOfOppositeName != null) {
                messageBuilder.append(", got '")
                        .append(oppositeOfOppositeName)
                        .append("' instead");
            }
            messageBuilder.append(".");
            acceptError(messageBuilder.toString(), referenceDeclaration,
                    ProblemPackage.Literals.REFERENCE_DECLARATION__OPPOSITE, 0, INVALID_OPPOSITE_ISSUE);
        }
    }

	@Check
	void checkContainerOpposite(ReferenceDeclaration referenceDeclaration) {
		var kind = referenceDeclaration.getKind();
		var opposite = referenceDeclaration.getOpposite();
		if (opposite != null && opposite.eIsProxy()) {
			// If {@code opposite} is a proxy, we have already emitted a linker error.
			return;
		}
		if (kind == ReferenceKind.CONTAINMENT) {
			if (opposite != null && opposite.getKind() == ReferenceKind.CONTAINMENT) {
				acceptError("Opposite '%s' of containment reference '%s' is not a container reference."
								.formatted(opposite.getName(), referenceDeclaration.getName()),
						referenceDeclaration, ProblemPackage.Literals.REFERENCE_DECLARATION__OPPOSITE, 0,
						INVALID_OPPOSITE_ISSUE);
			}
		} else if (kind == ReferenceKind.CONTAINER) {
			if (opposite == null) {
				acceptError("Container reference '%s' requires an opposite.".formatted(referenceDeclaration.getName()),
						referenceDeclaration, ProblemPackage.Literals.NAMED_ELEMENT__NAME, 0, MISSING_OPPOSITE_ISSUE);
			} else if (opposite.getKind() != ReferenceKind.CONTAINMENT) {
				acceptError("Opposite '%s' of container reference '%s' is not a containment reference."
								.formatted(opposite.getName(), referenceDeclaration.getName()),
						referenceDeclaration, ProblemPackage.Literals.REFERENCE_DECLARATION__OPPOSITE, 0,
						INVALID_OPPOSITE_ISSUE);
			}
		}
	}
}
