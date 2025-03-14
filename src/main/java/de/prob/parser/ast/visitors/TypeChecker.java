package de.prob.parser.ast.visitors;

import de.prob.parser.ast.nodes.DeclarationNode;
import de.prob.parser.ast.nodes.EnumeratedSetDeclarationNode;
import de.prob.parser.ast.nodes.FormulaNode;
import de.prob.parser.ast.nodes.MachineNode;
import de.prob.parser.ast.nodes.MachineReferenceNode;
import de.prob.parser.ast.nodes.Node;
import de.prob.parser.ast.nodes.OperationNode;
import de.prob.parser.ast.nodes.expression.RealNumberNode;
import de.prob.parser.ast.nodes.expression.RecordFieldAccessNode;
import de.prob.parser.ast.nodes.expression.RecordNode;
import de.prob.parser.ast.nodes.expression.StructNode;
import de.prob.parser.ast.nodes.TypedNode;
import de.prob.parser.ast.nodes.expression.ExprNode;
import de.prob.parser.ast.nodes.expression.ExpressionOperatorNode;
import de.prob.parser.ast.nodes.expression.IdentifierExprNode;
import de.prob.parser.ast.nodes.expression.IfExpressionNode;
import de.prob.parser.ast.nodes.expression.LambdaNode;
import de.prob.parser.ast.nodes.expression.LetExpressionNode;
import de.prob.parser.ast.nodes.expression.NumberNode;
import de.prob.parser.ast.nodes.expression.QuantifiedExpressionNode;
import de.prob.parser.ast.nodes.expression.SetComprehensionNode;
import de.prob.parser.ast.nodes.expression.StringNode;
import de.prob.parser.ast.nodes.ltl.LTLBPredicateNode;
import de.prob.parser.ast.nodes.ltl.LTLFormula;
import de.prob.parser.ast.nodes.ltl.LTLInfixOperatorNode;
import de.prob.parser.ast.nodes.ltl.LTLKeywordNode;
import de.prob.parser.ast.nodes.ltl.LTLPrefixOperatorNode;
import de.prob.parser.ast.nodes.predicate.CastPredicateExpressionNode;
import de.prob.parser.ast.nodes.predicate.IdentifierPredicateNode;
import de.prob.parser.ast.nodes.predicate.IfPredicateNode;
import de.prob.parser.ast.nodes.predicate.LetPredicateNode;
import de.prob.parser.ast.nodes.predicate.PredicateNode;
import de.prob.parser.ast.nodes.predicate.PredicateOperatorNode;
import de.prob.parser.ast.nodes.predicate.PredicateOperatorWithExprArgsNode;
import de.prob.parser.ast.nodes.predicate.QuantifiedPredicateNode;
import de.prob.parser.ast.nodes.substitution.AnySubstitutionNode;
import de.prob.parser.ast.nodes.substitution.AssignSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.BecomesElementOfSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.BecomesSuchThatSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.ChoiceSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.ConditionSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.IfOrSelectSubstitutionsNode;
import de.prob.parser.ast.nodes.substitution.LetSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.ListSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.OperationCallSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.SkipSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.SubstitutionNode;
import de.prob.parser.ast.nodes.substitution.VarSubstitutionNode;
import de.prob.parser.ast.nodes.substitution.WhileSubstitutionNode;
import de.prob.parser.ast.types.BType;
import de.prob.parser.ast.types.BoolType;
import de.prob.parser.ast.types.CoupleType;
import de.prob.parser.ast.types.DeferredSetElementType;
import de.prob.parser.ast.types.EnumeratedSetElementType;
import de.prob.parser.ast.types.IntegerOrSetOfPairs;
import de.prob.parser.ast.types.IntegerType;
import de.prob.parser.ast.types.RealType;
import de.prob.parser.ast.types.RecordType;
import de.prob.parser.ast.types.SetOrIntegerType;
import de.prob.parser.ast.types.SetType;
import de.prob.parser.ast.types.StringType;
import de.prob.parser.ast.types.UnificationException;
import de.prob.parser.ast.types.UntypedType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TypeChecker implements AbstractVisitor<BType, BType> {

	private static final String TYPE_ERROR = "TYPE_ERROR";

	private Set<ExpressionOperatorNode> minusNodes = new HashSet<>();
	private Set<ExpressionOperatorNode> multOrCartNodes = new HashSet<>();
	private Set<TypedNode> typedNodes = new HashSet<>();

	// TODO: Implement type checking for reals

	public TypeChecker(MachineNode machineNode) throws TypeErrorException {
		try {
			checkMachineNode(machineNode);
		} catch (TypeCheckerVisitorException e) {
			final Logger logger = Logger.getLogger(e.getClass().getName());
			logger.log(Level.SEVERE, TYPE_ERROR, e);
			throw e.getTypeErrorException();
		}
	}

	class TypeCheckerVisitorException extends RuntimeException {
		private static final long serialVersionUID = 1744515995462230895L;
		private final TypeErrorException typeErrorException;

		TypeCheckerVisitorException(TypeErrorException typeErrorException) {
			this.typeErrorException = typeErrorException;
		}

		public TypeErrorException getTypeErrorException() {
			return this.typeErrorException;
		}
	}

	private TypeChecker() {
	}

	private void checkFormulaNode(FormulaNode formulaNode) {
		for (DeclarationNode node : formulaNode.getImplicitDeclarations()) {
			setInitialType(node);
		}
		Node formula = formulaNode.getFormula();
		if (formula instanceof PredicateNode) {
			visitPredicateNode((PredicateNode) formula, BoolType.getInstance());
		} else {
			// expression formula
			BType type = visitExprNode((ExprNode) formula, new UntypedType());
			if (type.isUntyped()) {
				throw new TypeCheckerVisitorException(new TypeErrorException("Can not infer type of formula: " + type));
			}
		}

		// check that all implicitly declared variables have a type, otherwise
		// throw an exception
		for (DeclarationNode node : formulaNode.getImplicitDeclarations()) {
			if (node.getType().isUntyped()) {
				throw new TypeCheckerVisitorException(
						new TypeErrorException("Can not infer the type of local variable '" + node.getName()
								+ "' Current type: " + node.getType()));
			}
		}
		performPostActions();
	}


	public void checkExprNode(ExprNode exprNode) {
		BType type = visitExprNode(exprNode, new UntypedType());
		if (type.isUntyped()) {
			throw new TypeCheckerVisitorException(new TypeErrorException("Can not infer type of formula: " + type));
		}
		performPostActions();
	}

	public void checkPredicateNode(PredicateNode predNode) {
		BType type = visitPredicateNode(predNode, new UntypedType());
		if (type.isUntyped()) {
			throw new TypeCheckerVisitorException(new TypeErrorException("Can not infer type of formula: " + type));
		}
		performPostActions();
	}

	private void checkLTLFormulaNode(LTLFormula ltlFormulaAst) {
		for (DeclarationNode node : ltlFormulaAst.getImplicitDeclarations()) {
			node.setType(new UntypedType());
		}
		visitLTLNode(ltlFormulaAst.getLTLNode(), null);

		// check that all implicitly declared variables have a type, otherwise
		// throw an exception
		for (DeclarationNode node : ltlFormulaAst.getImplicitDeclarations()) {
			if (node.getType().isUntyped()) {
				throw new TypeCheckerVisitorException(
						new TypeErrorException("Can not infer the type of local variable '" + node.getName()
								+ "' Current type: " + node.getType()));
			}
		}
		performPostActions();
	}

	private void setInitialType(DeclarationNode node) {
		if (node.getType() == null) {
			node.setType(new UntypedType());
		}
	}

	private void checkMachineNode(MachineNode machineNode) {
		for (EnumeratedSetDeclarationNode eSet : machineNode.getEnumeratedSets()) {
			DeclarationNode setDeclaration = eSet.getSetDeclarationNode();
			if (setDeclaration.getType() == null) {
				EnumeratedSetElementType userDefinedElementType = new EnumeratedSetElementType(setDeclaration.getName(),
						eSet.getElementsAsStrings());
				setDeclaration.setType(new SetType(userDefinedElementType));
				for (DeclarationNode element : eSet.getElements()) {
					element.setType(userDefinedElementType);
				}
			}
		}

		for (DeclarationNode dSet : machineNode.getDeferredSets()) {
			if(dSet.getType() == null) {
				dSet.setType(new SetType(new DeferredSetElementType(dSet.getName())));
			}
		}

		// set all constants to untyped
		machineNode.getConstants().forEach(this::setInitialType);

		// visit the properties clause
		if (machineNode.getProperties() != null) {
			visitPredicateNode(machineNode.getProperties(), BoolType.getInstance());
		}

		// check that all constants have a type, otherwise throw an exception
		machineNode.getConstants().stream().filter(TypedNode::isUntyped).findFirst().ifPresent(con -> {
			throw new TypeCheckerVisitorException(new TypeErrorException(
					"Can not infer the type of constant " + con.getName() + ". Type variable: " + con.getType()));
		});

		machineNode.getValues().forEach(node -> visitSubstitutionNode(node, null));

		// set all variables to untyped
		machineNode.getVariables().forEach(this::setInitialType);
		machineNode.getIncludedRenamedVariables().forEach(this::setInitialType);
		typecheckRenamedVariables(machineNode);


		machineNode.getIncludedRenamedVariables().stream().filter(TypedNode::isUntyped).findFirst().ifPresent(var -> {
			throw new TypeCheckerVisitorException(new TypeErrorException(
					"Can not infer the type of variable " + var.getName() + ". Type variable: " + var.getType()));
		});

		// visit the invariant clause
		if (machineNode.getInvariant() != null) {
			visitPredicateNode(machineNode.getInvariant(), BoolType.getInstance());
		}

		// visit the assertion clause
		if (machineNode.getAssertions() != null) {
			machineNode.getAssertions().forEach(node -> visitPredicateNode(node, BoolType.getInstance()));
		}

		// check that all variables have type, otherwise throw an exception
		machineNode.getVariables().stream().filter(TypedNode::isUntyped).findFirst().ifPresent(var -> {
			throw new TypeCheckerVisitorException(new TypeErrorException(
					"Can not infer the type of variable " + var.getName() + ". Type variable: " + var.getType()));
		});

		// visit the initialisation clause
		if (machineNode.getInitialisation() != null) {
			visitSubstitutionNode(machineNode.getInitialisation(), null);
		}

		// visit all operations
		visitOperations(machineNode);

		performPostActions();
	}

	private void typecheckRenamedVariables(MachineNode machineNode) {
		if(machineNode.getIncludedRenamedVariables() != null) {
			for (MachineReferenceNode reference : machineNode.getMachineReferences()) {
				if (reference.getPrefix() != null) {
					String prefix = reference.getPrefix() + ".";
					for (DeclarationNode variable : machineNode.getIncludedRenamedVariables()) {
						if (variable.getName().startsWith(prefix)) {
							String identifier = variable.getName().replaceFirst(prefix, "");
							for (DeclarationNode otherVariable : reference.getMachineNode().getVariables()) {
								if (identifier.equals(otherVariable.getName())) {
									variable.setType(otherVariable.getType());
								}
							}
						}
						for (DeclarationNode otherVariable : machineNode.getIncludedRenamedVariables()) {
							String includedMachinePrefix = variable.getSurroundingMachineNode().getPrefix();
							if(includedMachinePrefix != null) {
								if (variable.getName().startsWith(includedMachinePrefix) && otherVariable.getName().startsWith(includedMachinePrefix) && variable.getName().equals(otherVariable.getName())) {
									variable.setType(otherVariable.getType());
								}
							}
						}
					}
				} else {
					for (DeclarationNode variable : machineNode.getIncludedRenamedVariables()) {
						for (DeclarationNode otherVariable : reference.getMachineNode().getIncludedRenamedVariables()) {
							if (variable.getName().equals(otherVariable.getName())) {
								variable.setType(otherVariable.getType());
							}
						}
					}
				}

			}
		}
	}

	private void visitOperations(MachineNode machineNode) {
		for (OperationNode operationsNode : machineNode.getOperations()) {
			setDeclarationTypes(operationsNode.getOutputParams());
			setDeclarationTypes(operationsNode.getParams());
			visitSubstitutionNode(operationsNode.getSubstitution(), null);
		}
	}

	private void performPostActions() {
		// Check that all local variables have type.
		for (TypedNode node : typedNodes) {
			if (node.getType().isUntyped()) {
				if (node instanceof DeclarationNode) {
					DeclarationNode var = (DeclarationNode) node;
					throw new TypeCheckerVisitorException(new TypeErrorException(
							"Can not infer the type of local variable " + var.getName() + ": " + node.getType()));
				} else if (node instanceof ExpressionOperatorNode) {
					ExpressionOperatorNode exprNode = (ExpressionOperatorNode) node;
					throw new TypeCheckerVisitorException(
							new TypeErrorException("Can not infer the complete type of operator "
									+ exprNode.getOperator() + ": " + node.getType()));
				}
			}
		}

		// post actions
		for (ExpressionOperatorNode minusNode : minusNodes) {
			BType type = minusNode.getType();
			if (type instanceof SetType) {
				minusNode.setOperator(ExpressionOperatorNode.ExpressionOperator.SET_SUBTRACTION);
			}
		}

		// post actions
		for (ExpressionOperatorNode node : multOrCartNodes) {
			BType type = node.getType();
			if (type instanceof SetType) {
				node.setOperator(ExpressionOperatorNode.ExpressionOperator.CARTESIAN_PRODUCT);
			}
		}
	}

	@Override
	public BType visitPredicateOperatorNode(PredicateOperatorNode node, BType expected) {
		unify(expected, BoolType.getInstance(), node);
		List<PredicateNode> predicateArguments = node.getPredicateArguments();
		for (PredicateNode predicateNode : predicateArguments) {
			visitPredicateNode(predicateNode, BoolType.getInstance());
		}
		return BoolType.getInstance();
	}

	@Override
	public BType visitPredicateOperatorWithExprArgs(PredicateOperatorWithExprArgsNode node, BType expected) {
		unify(expected, BoolType.getInstance(), node);
		final List<ExprNode> expressionNodes = node.getExpressionNodes();
		switch (node.getOperator()) {
		case EQUAL:
		case NOT_EQUAL: {
			visitExprNode(expressionNodes.get(0), visitExprNode(expressionNodes.get(1), new UntypedType()));
			break;
		}
		case NOT_BELONGING:
		case ELEMENT_OF: {
			BType left = visitExprNode(expressionNodes.get(0), new UntypedType());
			visitExprNode(expressionNodes.get(1), new SetType(left));
			break;
		}
		case LESS_EQUAL:
		case LESS:
		case GREATER_EQUAL:
		case GREATER:
			visitExprNode(expressionNodes.get(0), IntegerType.getInstance());
			visitExprNode(expressionNodes.get(1), IntegerType.getInstance());
			break;
		case INCLUSION:
		case NON_INCLUSION:
		case STRICT_INCLUSION:
		case STRICT_NON_INCLUSION: {
			BType left = visitExprNode(expressionNodes.get(0), new SetType(new UntypedType()));
			visitExprNode(expressionNodes.get(1), left);
			break;
		}
		default:
			throw new AssertionError("Not implemented");
		}
		return BoolType.getInstance();
	}

	@Override
	public BType visitExprOperatorNode(ExpressionOperatorNode node, BType expected) {
		List<ExprNode> expressionNodes = node.getExpressionNodes();
		switch (node.getOperator()) {
		case PLUS:
		case UNARY_MINUS:
		case MOD:
		case DIVIDE:
		case PRED:
		case SUCC:
		case POWER_OF:
			expressionNodes.forEach(n -> visitExprNode(n, IntegerType.getInstance()));
			return unify(expected, IntegerType.getInstance(), node);
		case MULT: {
			BType found = new IntegerOrSetOfPairs(new UntypedType(), new UntypedType());
			found = unify(expected, found, node);
			ExprNode left = expressionNodes.get(0);
			ExprNode right = expressionNodes.get(1);
			if (found instanceof IntegerType) {
				visitExprNode(left, IntegerType.getInstance());
				visitExprNode(right, IntegerType.getInstance());
			} else if (found instanceof SetType) {
				SetType setType = (SetType) found;
				CoupleType coupleType = (CoupleType) setType.getSubType();
				visitExprNode(left, new SetType(coupleType.getLeft()));
				visitExprNode(right, new SetType(coupleType.getRight()));
			} else if (found instanceof IntegerOrSetOfPairs) {
				IntegerOrSetOfPairs integerOrSetOfPairs = (IntegerOrSetOfPairs) found;
				BType leftType = visitExprNode(left, integerOrSetOfPairs.getLeft());
				if (leftType instanceof IntegerType) {
					visitExprNode(right, IntegerType.getInstance());
				} else if (leftType instanceof SetType) {
					SetType s = (SetType) node.getType();
					CoupleType c = (CoupleType) s.getSubType();
					visitExprNode(right, new SetType(c.getRight()));
				} else {
					IntegerOrSetOfPairs s = (IntegerOrSetOfPairs) node.getType();
					visitExprNode(right, s.getRight());
				}
			} else {
				throw new AssertionError();
			}
			this.multOrCartNodes.add(node);
			this.typedNodes.add(node);
			return node.getType();
		}
		case MINUS:
			unify(expected, new SetOrIntegerType(new UntypedType()), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), node.getType());
			this.minusNodes.add(node);
			this.typedNodes.add(node);
			return node.getType();
		case INTERVAL:
			unify(expected, new SetType(IntegerType.getInstance()), node);
			visitExprNode(expressionNodes.get(0), IntegerType.getInstance());
			visitExprNode(expressionNodes.get(1), IntegerType.getInstance());
			return node.getType();
		case SET_ENUMERATION: {
			SetType found = (SetType) unify(expected, new SetType(new UntypedType()), node);
			BType subtype = found.getSubType();
			for (ExprNode exprNode : expressionNodes) {
				subtype = visitExprNode(exprNode, subtype);
			}
			return node.getType();
		}
		case MIN:
		case MAX:
			unify(expected, IntegerType.getInstance(), node);
			visitExprNode(expressionNodes.get(0), new SetType(IntegerType.getInstance()));
			return node.getType();
		case MININT:
		case MAXINT:
			return unify(expected, IntegerType.getInstance(), node);
		case INTEGER:
		case NATURAL:
		case NATURAL1:
		case INT:
		case NAT:
		case NAT1:
			return unify(expected, new SetType(IntegerType.getInstance()), node);
		case STRING:
			return unify(expected, new SetType(StringType.getInstance()), node);
		case FALSE:
		case TRUE:
			return unify(expected, BoolType.getInstance(), node);
		case BOOL:
			return unify(expected, new SetType(BoolType.getInstance()), node);
		case SET_SUBTRACTION:
		case INTERSECTION:
		case UNION:
			unify(expected, new SetType(new UntypedType()), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), node.getType());
			return node.getType();
		case COUPLE:
			return unify(expected, new CoupleType(visitExprNode(expressionNodes.get(0), new UntypedType()),
					visitExprNode(expressionNodes.get(1), new UntypedType())), node);
		case DOMAIN: {
			SetType argument = new SetType(new CoupleType(new UntypedType(), new UntypedType()));
			argument = (SetType) visitExprNode(expressionNodes.get(0), argument);
			CoupleType subType = (CoupleType) argument.getSubType();
			SetType found = new SetType(subType.getLeft());
			unify(expected, found, node);
			return node.getType();
		}
		case RANGE: {
			SetType setType = (SetType) unify(expected, new SetType(new UntypedType()), node);
			visitExprNode(expressionNodes.get(0), new SetType(new CoupleType(new UntypedType(), setType.getSubType())));
			return node.getType();
		}
		case ID: {
			SetType argument = (SetType) visitExprNode(expressionNodes.get(0), new UntypedType());
			BType subType = argument.getSubType();
			unify(expected, new SetType(new CoupleType(subType, subType)), node);
			return node.getType();
		}
		case CLOSURE:
		case CLOSURE1:
			unify(expected, new SetType(new CoupleType(new UntypedType(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			return node.getType();
		case ITERATE:
			unify(expected, new SetType(new CoupleType(new UntypedType(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), IntegerType.getInstance());
			return node.getType();
		case PRJ1: {
			unify(expected, new SetType(new CoupleType(new CoupleType(new UntypedType(), new UntypedType()), new UntypedType())), node);
			CoupleType coupleType = (CoupleType) ((SetType) node.getType()).getSubType();
			CoupleType domainCoupleType = (CoupleType) coupleType.getLeft();
			BType rangeCoupleType = coupleType.getRight();
			BType leftDomainType = domainCoupleType.getLeft();
			BType rightDomainType = domainCoupleType.getRight();
			visitExprNode(expressionNodes.get(0), new SetType(leftDomainType));
			visitExprNode(expressionNodes.get(1), new SetType(rightDomainType));
			visitExprNode(expressionNodes.get(0), new SetType(rangeCoupleType));
			return node.getType();
		}
		case PRJ2: {
			unify(expected, new SetType(new CoupleType(new CoupleType(new UntypedType(), new UntypedType()), new UntypedType())), node);
			CoupleType coupleType = (CoupleType) ((SetType) node.getType()).getSubType();
			CoupleType domainCoupleType = (CoupleType) coupleType.getLeft();
			BType rangeCoupleType = coupleType.getRight();
			BType leftDomainType = domainCoupleType.getLeft();
			BType rightDomainType = domainCoupleType.getRight();
			visitExprNode(expressionNodes.get(0), new SetType(leftDomainType));
			visitExprNode(expressionNodes.get(1), new SetType(rightDomainType));
			visitExprNode(expressionNodes.get(1), new SetType(rangeCoupleType));
			return node.getType();
		}
		case FNC: {
			unify(expected, new SetType(new CoupleType(new UntypedType(), new SetType(new UntypedType()))), node);
			CoupleType coupleType = (CoupleType) ((SetType) node.getType()).getSubType();
			BType leftType = coupleType.getLeft();
			SetType rightSetType = (SetType) coupleType.getRight();
			BType rightType = rightSetType.getSubType();
			visitExprNode(expressionNodes.get(0), new SetType(new CoupleType(leftType, rightType)));
			return node.getType();
		}
		case REL: {
			unify(expected, new SetType(new CoupleType(new UntypedType(), new UntypedType())), node);
			CoupleType coupleType = (CoupleType) ((SetType) node.getType()).getSubType();
			BType leftType = coupleType.getLeft();
			BType rightType = coupleType.getRight();
			visitExprNode(expressionNodes.get(0), new SetType(new CoupleType(leftType, new SetType(rightType))));
			return node.getType();
		}
		case CONCAT:
			unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), node.getType());
			return node.getType();
		case CONC:
			unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
			if(!expressionNodes.isEmpty()) {
				visitExprNode(expressionNodes.get(0), new SetType(new CoupleType(IntegerType.getInstance(), node.getType())));
			}
			return node.getType();
		case CARTESIAN_PRODUCT: {
			SetType found = new SetType(new CoupleType(new UntypedType(), new UntypedType()));
			found = (SetType) unify(expected, found, node);
			CoupleType c1 = (CoupleType) found.getSubType();
			BType typeOfA = c1.getLeft();
			BType typeOfB = c1.getRight();
			visitExprNode(expressionNodes.get(0), new SetType(typeOfA));
			visitExprNode(expressionNodes.get(1), new SetType(typeOfB));
			return node.getType();
		}
		case DIRECT_PRODUCT: {
			/*
			 * E ⊗ F type of result is is P(T ×(U × V)) type of E is P(T × U)
			 * type of F is P(T × V)
			 *
			 */
			SetType found = new SetType(
					new CoupleType(new UntypedType(), new CoupleType(new UntypedType(), new UntypedType())));
			found = (SetType) unify(expected, found, node);
			CoupleType c1 = (CoupleType) found.getSubType();
			CoupleType c2 = (CoupleType) c1.getRight();
			BType typeOfT = c1.getLeft();
			BType typeOfU = c2.getLeft();
			BType typeOfV = c2.getRight();
			SetType leftArg = (SetType) visitExprNode(expressionNodes.get(0),
					new SetType(new CoupleType(typeOfT, typeOfU)));
			typeOfT = ((CoupleType) leftArg.getSubType()).getLeft();
			visitExprNode(expressionNodes.get(1), new SetType(new CoupleType(typeOfT, typeOfV)));
			return node.getType();
		}
		case PARALLEL_PRODUCT: {
			SetType found = new SetType(
					new CoupleType(new CoupleType(new UntypedType(), new UntypedType()), new CoupleType(new UntypedType(), new UntypedType())));
			found = (SetType) unify(expected, found, node);
			CoupleType c1 = (CoupleType) found.getSubType();
			CoupleType c2 = (CoupleType) c1.getLeft();
			CoupleType c3 = (CoupleType) c1.getRight();
			BType typeOfA = c2.getLeft();
			BType typeOfV = c3.getLeft();
			BType typeOfB = c2.getRight();
			BType typeOfW = c3.getRight();
			visitExprNode(expressionNodes.get(0),
					new SetType(new CoupleType(typeOfA, typeOfB)));
			visitExprNode(expressionNodes.get(1), new SetType(new CoupleType(typeOfV, typeOfW)));
			return node.getType();
		}
		case COMPOSITION:
			SetType left = (SetType) visitExprNode(expressionNodes.get(0), new SetType(new CoupleType(new UntypedType(), new UntypedType())));
			SetType right = (SetType) visitExprNode(expressionNodes.get(1), new SetType(new CoupleType(new UntypedType(), new UntypedType())));
			CoupleType coupleTypeLeft = (CoupleType) left.getSubType();
			CoupleType coupleRightType = (CoupleType) right.getSubType();
			unify(expected, new SetType(new CoupleType(coupleTypeLeft.getLeft(), coupleRightType.getRight())), node);
			return node.getType();
		case DOMAIN_RESTRICTION:
		case DOMAIN_SUBTRACTION:
			// S <| r
			// S <<| r
			unify(expected, createNewRelationType(), node);
			visitExprNode(expressionNodes.get(1), node.getType());
			visitExprNode(expressionNodes.get(0), new SetType(getLeftTypeOfRelationType(node.getType())));
			return node.getType();
		case RANGE_RESTRICTION:
		case RANGE_SUBTRACTION:
			// r |> S
			// r |>> S
			unify(expected, createNewRelationType(), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), new SetType(getRightTypeOfRelationType(node.getType())));
			return node.getType();
		case INSERT_FRONT:
			// E -> s
			unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(1), node.getType());
			visitExprNode(expressionNodes.get(0), getRightTypeOfRelationType(node.getType()));
			return node.getType();
		case INSERT_TAIL:
			// s <- E
			unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), getRightTypeOfRelationType(node.getType()));
			return node.getType();
		case OVERWRITE_RELATION:
			unify(expected, new SetType(new CoupleType(new UntypedType(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), node.getType());
			return node.getType();
		case INVERSE_RELATION: {
			SetType argType = (SetType) visitExprNode(expressionNodes.get(0),
					new SetType(new CoupleType(new UntypedType(), new UntypedType())));
			CoupleType c = (CoupleType) argType.getSubType();
			return unify(expected, new SetType(new CoupleType(c.getRight(), c.getLeft())), node);
		}
		case RESTRICT_FRONT:
		case RESTRICT_TAIL:
			// s /|\ n s \|/ n
			// type of result is P(Z × T)
			// type of s is P(Z×T)
			// type of n is INTEGER
			unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
			visitExprNode(expressionNodes.get(0), node.getType());
			visitExprNode(expressionNodes.get(1), IntegerType.getInstance());
			return node.getType();
		case GENERALIZED_INTER:
		case GENERALIZED_UNION:
			// inter(S), union(S)
			// type of result is Z
			// type of S is P(Z)
			unify(expected, new SetType(new UntypedType()), node);
			visitExprNode(expressionNodes.get(0), new SetType(node.getType()));
			return node.getType();
		case EMPTY_SEQUENCE:
			typedNodes.add(node);
			return unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
		case SEQ_ENUMERATION: {
			SetType found = (SetType) unify(expected,
					new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node);
			BType elementType = ((CoupleType) found.getSubType()).getRight();
			for (ExprNode exprNode : expressionNodes) {
				elementType = visitExprNode(exprNode, elementType);
			}
			return node.getType();
		}
		case LAST:
		case FIRST:
			return unify(expected, getRightTypeOfRelationType(visitExprNode(expressionNodes.get(0),
					new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())))), node);
		case REV:
		case FRONT:
		case TAIL:
			return visitExprNode(expressionNodes.get(0),
					unify(expected, new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType())), node));
		case PERM:
		case SEQ:
		case SEQ1:
		case ISEQ:
		case ISEQ1: {
			SetType found = (SetType) unify(expected,
					new SetType(new SetType(new CoupleType(IntegerType.getInstance(), new UntypedType()))), node);
			SetType type = (SetType) found.getSubType();
			CoupleType coupleType = (CoupleType) type.getSubType();
			visitExprNode(expressionNodes.get(0), new SetType(coupleType.getRight()));
			return node.getType();
		}
		case FUNCTION_CALL: {
			// visit arguments
			List<ExprNode> arguments = expressionNodes.stream().filter(e -> expressionNodes.get(0) != e)
					.collect(Collectors.toList());
			arguments.forEach(e -> visitExprNode(e, new UntypedType()));

			// collect types of arguments; Note, this should be done after all
			// arguments have been visited because the type object of a previous
			// argument could change.
			List<BType> typesList = arguments.stream().map(ExprNode::getType).collect(Collectors.toList());
			BType domType = createNestedCouple(typesList);

			// visit function base
			SetType baseType = (SetType) visitExprNode(expressionNodes.get(0),
					new SetType(new CoupleType(domType, new UntypedType())));
			return unify(expected, ((CoupleType) baseType.getSubType()).getRight(), node);
		}
		case RELATIONAL_IMAGE: {
			SetType relType = (SetType) visitExprNode(expressionNodes.get(0),
					new SetType(new CoupleType(new UntypedType(), new UntypedType())));
			CoupleType coupleType = (CoupleType) relType.getSubType();
			visitExprNode(expressionNodes.get(1), new SetType(coupleType.getLeft()));
			// relType could have been changed
			relType = (SetType) expressionNodes.get(0).getType();
			coupleType = (CoupleType) relType.getSubType();
			return unify(expected, new SetType(coupleType.getRight()), node);
		}
		case SIZE:
		case CARD:
			visitExprNode(expressionNodes.get(0), new SetType(new UntypedType()));
			return unify(expected, IntegerType.getInstance(), node);
		case EMPTY_SET:
			typedNodes.add(node);
			return unify(expected, new SetType(new UntypedType()), node);
		case TOTAL_FUNCTION:
		case PARTIAL_FUNCTION:
		case TOTAL_INJECTION:
		case PARTIAL_INJECTION:
		case TOTAL_BIJECTION:
		case PARTIAL_BIJECTION:
		case TOTAL_SURJECTION:
		case PARTIAL_SURJECTION:
		case SURJECTION_RELATION:
		case TOTAL_RELATION:
		case TOTAL_SURJECTION_RELATION:
		case SET_RELATION: {
			SetType found = new SetType(new SetType(new CoupleType(new UntypedType(), new UntypedType())));
			found = (SetType) unify(expected, found, node);
			SetType set = (SetType) found.getSubType();
			CoupleType couple = (CoupleType) set.getSubType();
			visitExprNode(node.getExpressionNodes().get(0), new SetType(couple.getLeft()));
			visitExprNode(node.getExpressionNodes().get(1), new SetType(couple.getRight()));
			return node.getType();
		}
		case FIN:
		case FIN1:
		case POW1:
		case POW:
			SetType found = (SetType) unify(expected, new SetType(new UntypedType()), node);
			BType subtype = found.getSubType();
			for (ExprNode exprNode : expressionNodes) {
				subtype = visitExprNode(exprNode, subtype);
			}
			return node.getType();
		default:
			throw new AssertionError();
		}
	}

	private SetType createNewRelationType() {
		return new SetType(new CoupleType(new UntypedType(), new UntypedType()));
	}

	private BType getRightTypeOfRelationType(BType s) {
		SetType setType = (SetType) s;
		CoupleType c = (CoupleType) setType.getSubType();
		return c.getRight();
	}

	private BType getLeftTypeOfRelationType(BType s) {
		SetType setType = (SetType) s;
		CoupleType c = (CoupleType) setType.getSubType();
		return c.getLeft();
	}

	@Override
	public BType visitIdentifierExprNode(IdentifierExprNode node, BType expected) {
		if(node.getDeclarationNode() == null || node.getDeclarationNode().getType() == null) {
			//TODO: Implement scoping and type checking of included sets
			return unify(expected, new UntypedType(), node);
		}
		return unify(expected, node.getDeclarationNode().getType(), node);
	}

	@Override
	public BType visitCastPredicateExpressionNode(CastPredicateExpressionNode node, BType expected) {
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		return unify(expected, BoolType.getInstance(), node);
	}

	@Override
	public BType visitIdentifierPredicateNode(IdentifierPredicateNode node, BType expected) {
		return unify(expected, node.getDeclarationNode().getType(), node);
	}

	@Override
	public BType visitNumberNode(NumberNode node, BType expected) {
		return unify(expected, IntegerType.getInstance(), node);
	}

	@Override
	public BType visitRealNumberNode(RealNumberNode node, BType expected) {
		return unify(expected, RealType.getInstance(), node);
	}

	private BType unify(BType expected, BType found, TypedNode node) {
		try {
			BType type = found.unify(expected);
			node.setType(type);
			return type;
		} catch (UnificationException e) {
			throw new TypeCheckerVisitorException(new TypeErrorException(expected, found, node, e));
		}
	}

	private void setDeclarationTypes(List<DeclarationNode> list) {
		for (DeclarationNode decl : list) {
			if (decl.getType() == null) {
				decl.setType(new UntypedType());
			}
			this.typedNodes.add(decl);
		}
	}

	@Override
	public BType visitQuantifiedExpressionNode(QuantifiedExpressionNode node, BType expected) {
		setDeclarationTypes(node.getDeclarationList());
		visitPredicateNode(node.getPredicateNode(), BoolType.getInstance());
		switch (node.getOperator()) {
		case QUANTIFIED_INTER:
		case QUANTIFIED_UNION:
			unify(expected, new SetType(new UntypedType()), node);
			visitPredicateNode(node.getPredicateNode(), BoolType.getInstance());
			visitExprNode(node.getExpressionNode(), node.getType());
			return node.getType();
		case SIGMA:
		case PI:
			unify(expected, IntegerType.getInstance(), node);
			visitPredicateNode(node.getPredicateNode(), BoolType.getInstance());
			visitExprNode(node.getExpressionNode(), node.getType());
			return node.getType();
		default:
			break;
		}
		throw new AssertionError("Not implemented.");
	}

	@Override
	public BType visitSetComprehensionNode(SetComprehensionNode node, BType expected) {
		setDeclarationTypes(node.getDeclarationList());
		visitPredicateNode(node.getPredicateNode(), BoolType.getInstance());
		List<BType> types = node.getDeclarationList().stream().map(TypedNode::getType).collect(Collectors.toList());
		return unify(expected, new SetType(createNestedCouple(types)), node);
	}

	@Override
	public BType visitQuantifiedPredicateNode(QuantifiedPredicateNode node, BType expected) {
		setDeclarationTypes(node.getDeclarationList());
		visitPredicateNode(node.getPredicateNode(), BoolType.getInstance());
		return unify(expected, BoolType.getInstance(), node);
	}

	/*
	 * Substitutions
	 */

	@Override
	public BType visitIfOrSelectSubstitutionsNode(IfOrSelectSubstitutionsNode node, BType expected) {
		node.getConditions().forEach(t -> visitPredicateNode(t, BoolType.getInstance()));
		node.getSubstitutions().forEach(t -> visitSubstitutionNode(t, null));
		if (node.getElseSubstitution() != null) {
			visitSubstitutionNode(node.getElseSubstitution(), null);
		}
		return null;
	}

	@Override
	public BType visitIfExpressionNode(IfExpressionNode node, BType expected) {
		visitPredicateNode(node.getCondition(), BoolType.getInstance());
		BType type = visitExprNode(node.getThenExpression(), new UntypedType());
		return unify(expected, visitExprNode(node.getElseExpression(), type), node);
	}

	@Override
	public BType visitStringNode(StringNode node, BType expected) {
		return unify(expected, StringType.getInstance(), node);
	}

	@Override
	public BType visitRecordNode(RecordNode node, BType expected) {
		List<String> identifiers = node.getDeclarations().stream().map(DeclarationNode::getName).collect(Collectors.toList());
		setDeclarationTypes(node.getDeclarations());
		for(int i = 0; i < node.getDeclarations().size(); i++) {
			BType left = node.getDeclarations().get(i).getType();
			visitExprNode(node.getExpressions().get(i), left);
		}
		List<BType> types = node.getDeclarations().stream().map(TypedNode::getType).collect(Collectors.toList());
		unify(expected, new RecordType(identifiers, types), node);
		return node.getType();
	}

	@Override
	public BType visitStructNode(StructNode node, BType expected) {
		List<String> identifiers = node.getDeclarations().stream().map(DeclarationNode::getName).collect(Collectors.toList());
		setDeclarationTypes(node.getDeclarations());
		for(int i = 0; i < node.getDeclarations().size(); i++) {
			BType left = node.getDeclarations().get(i).getType();
			visitExprNode(node.getExpressions().get(i), new SetType(left));
		}
		List<BType> types = node.getDeclarations().stream().map(TypedNode::getType).collect(Collectors.toList());
		unify(expected, new SetType(new RecordType(identifiers, types)), node);
		return node.getType();
	}

	@Override
	public BType visitRecordFieldAccessNode(RecordFieldAccessNode node, BType expected) {
		RecordType recordType = (RecordType) visitExprNode(node.getRecord(), new UntypedType());
		DeclarationNode identifier = node.getIdentifier();
		for(int i = 0; i < recordType.getIdentifiers().size(); i++) {
			if(recordType.getIdentifiers().get(i).equals(identifier.getName())) {
				identifier.setType(recordType.getSubtypes().get(i));
				unify(expected, recordType.getSubtypes().get(i), node);
				break;
			}
		}
		return node.getType();
	}

	@Override
	public BType visitIfPredicateNode(IfPredicateNode node, BType expected) {
		visitPredicateNode(node.getCondition(), BoolType.getInstance());
		visitPredicateNode(node.getThenPredicate(), BoolType.getInstance());
		visitPredicateNode(node.getElsePredicate(), BoolType.getInstance());
		return unify(expected, BoolType.getInstance(), node);
	}

	@Override
	public BType visitConditionSubstitutionNode(ConditionSubstitutionNode node, BType expected) {
		visitPredicateNode(node.getCondition(), BoolType.getInstance());
		visitSubstitutionNode(node.getSubstitution(), null);
		return null;
	}

	@Override
	public BType visitAssignSubstitutionNode(AssignSubstitutionNode node, BType expected) {
		for (int i = 0; i < node.getLeftSide().size(); i++) {
			BType type = visitExprNode(node.getLeftSide().get(i), new UntypedType());
			visitExprNode(node.getRightSide().get(i), type);
		}
		return null;
	}

	@Override
	public BType visitAnySubstitution(AnySubstitutionNode node, BType expected) {
		setDeclarationTypes(node.getParameters());
		visitPredicateNode(node.getWherePredicate(), BoolType.getInstance());
		visitSubstitutionNode(node.getThenSubstitution(), null);
		return null;
	}

	@Override
	public BType visitLetSubstitution(LetSubstitutionNode node, BType expected) {
		setDeclarationTypes(node.getLocalIdentifiers());
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		visitSubstitutionNode(node.getBody(), null);
		return null;
	}

	@Override
	public BType visitLetExpressionNode(LetExpressionNode node, BType expected) {
		setDeclarationTypes(node.getLocalIdentifiers());
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		BType type = visitExprNode(node.getExpression(), new UntypedType());
		return unify(expected, type, node);
	}

	@Override
	public BType visitLetPredicateNode(LetPredicateNode node, BType expected) {
		setDeclarationTypes(node.getLocalIdentifiers());
		visitPredicateNode(node.getWherePredicate(), BoolType.getInstance());
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		return unify(expected, BoolType.getInstance(), node);
	}

	@Override
	public BType visitBecomesElementOfSubstitutionNode(BecomesElementOfSubstitutionNode node, BType expected) {
		List<BType> types = node.getIdentifiers().stream().map(t -> visitIdentifierExprNode(t, new UntypedType()))
				.collect(Collectors.toList());
		SetType type = new SetType(createNestedCouple(types));
		visitExprNode(node.getExpression(), type);
		return null;
	}

	private BType createNestedCouple(List<BType> types) {
		BType left = types.get(0);
		for (int i = 1; i < types.size(); i++) {
			left = new CoupleType(left, types.get(i));
		}
		return left;
	}

	@Override
	public BType visitBecomesSuchThatSubstitutionNode(BecomesSuchThatSubstitutionNode node, BType expected) {
		node.getIdentifiers().forEach(t -> visitIdentifierExprNode(t, new UntypedType()));
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		return null;
	}

	@Override
	public BType visitSkipSubstitutionNode(SkipSubstitutionNode node, BType expected) {
		return null;
	}

	@Override
	public BType visitLTLPrefixOperatorNode(LTLPrefixOperatorNode node, BType expected) {
		visitLTLNode(node.getArgument(), expected);
		return null;
	}

	@Override
	public BType visitLTLKeywordNode(LTLKeywordNode node, BType expected) {
		return null;
	}

	@Override
	public BType visitLTLInfixOperatorNode(LTLInfixOperatorNode node, BType expected) {
		visitLTLNode(node.getLeft(), expected);
		visitLTLNode(node.getRight(), expected);
		return null;
	}

	@Override
	public BType visitLTLBPredicateNode(LTLBPredicateNode node, BType expected) {
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		return null;
	}

	@Override
	public BType visitListSubstitutionNode(ListSubstitutionNode node, BType expected) {
		for (SubstitutionNode sub : node.getSubstitutions()) {
			visitSubstitutionNode(sub, expected);
		}
		return null;
	}

	@Override
	public BType visitSubstitutionIdentifierCallNode(OperationCallSubstitutionNode node, BType expected) {
		for (int i = 0; i < node.getArguments().size(); i++) {
			ExprNode arg = node.getArguments().get(i);
			BType type = node.getOperationNode().getParams().get(i).getType();
			visitExprNode(arg, type);
		}
		for (int i = 0; i < node.getAssignedVariables().size(); i++) {
			ExprNode var = node.getAssignedVariables().get(i);
			BType type = node.getOperationNode().getOutputParams().get(i).getType();
			visitExprNode(var, type);
		}
		return null;
	}

	@Override
	public BType visitWhileSubstitutionNode(WhileSubstitutionNode node, BType expected) {
		visitPredicateNode(node.getCondition(), BoolType.getInstance());
		visitSubstitutionNode(node.getBody(), expected);
		visitPredicateNode(node.getInvariant(), BoolType.getInstance());
		visitExprNode(node.getVariant(), IntegerType.getInstance());
		return null;
	}

	@Override
	public BType visitChoiceSubstitutionNode(ChoiceSubstitutionNode node, BType expected) {
		for(SubstitutionNode substitution : node.getSubstitutions()) {
			visitSubstitutionNode(substitution, expected);
		}
		return null;
	}

	@Override
	public BType visitVarSubstitutionNode(VarSubstitutionNode node, BType expected) {
		setDeclarationTypes(node.getLocalIdentifiers());
		visitSubstitutionNode(node.getBody(), null);
		return null;
	}

	@Override
	public BType visitLambdaNode(LambdaNode node, BType expected) {
		setDeclarationTypes(node.getDeclarations());
		visitPredicateNode(node.getPredicate(), BoolType.getInstance());
		List<BType> types = node.getDeclarations().stream().map(TypedNode::getType).collect(Collectors.toList());
		BType expressionType = visitExprNode(node.getExpression(), new UntypedType());
		return unify(expected, new SetType(new CoupleType(createNestedCouple(types), expressionType)), node);
	}

}
