package de.prob.parser.ast.nodes;


import de.prob.parser.ast.SourceCodePosition;

public class DeclarationNode extends TypedNode {

	private final String name;

	public DeclarationNode(SourceCodePosition sourceCodePosition, String name) {
		super(sourceCodePosition);
		this.name = name;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equalAst(Node other) {
		return NodeUtil.isSameClass(this, other) && this.name.equals(((DeclarationNode) other).name);
	}
}
