package jcodelib.parser;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.ArrayList;
import java.util.List;


public class TreeNode {
    public static final String DELIM = "|#|";
    public static final String ROOT = "root";

    private ASTNode astNode;
    private String label;
    private TreeNode parent;
    public List<TreeNode> children;
    private int lineNumber;

    public String hash;
    private boolean matched;

    public TreeNode() {
        this(ROOT, null, true);
    }

    public TreeNode(String label, ASTNode node, boolean matched) {
        super();
        this.astNode = node;
        this.label = label;
        this.parent = null;
        this.children = new ArrayList<>();
        this.lineNumber = computeLineNumber();
        this.matched = matched;
    }

    public boolean isLeaf() {
        return children.size() == 0;
    }

    public boolean isRoot() {
        return label == ROOT;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public TreeNode getParent() {
        return parent;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public int getType() {
        return astNode != null ? astNode.getNodeType() : -1;
    }

    public int getStartPosition() {
        return astNode.getStartPosition();
    }

    public int getLength() {
        return astNode.getLength();
    }

    public int getEndPosition() {
        return astNode.getStartPosition() + astNode.getLength();
    }

    public ASTNode getASTNode() {
        return astNode;
    }

    public int computeLineNumber() {
        if (astNode != null && astNode.getRoot() instanceof CompilationUnit) {
            return ((CompilationUnit) astNode.getRoot()).getLineNumber(astNode.getStartPosition());
        } else {
            return -1;
        }
    }

    public int indexInParent() {
        if (parent == null)
            return -1;
        return parent.children.indexOf(this);
    }

    public void addChild(TreeNode child) {
        children.add(child);
        child.setParent(this);
    }

    @Override
    public String toString() {
        return label + "(" + lineNumber + ")";
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }
}
