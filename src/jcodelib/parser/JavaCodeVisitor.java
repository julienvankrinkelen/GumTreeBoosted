package jcodelib.parser;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class JavaCodeVisitor extends ASTVisitor {

    private final MessageDigest md5;
    private Stack<TreeNode> nodeStack;

    public JavaCodeVisitor(TreeNode root) {
        this.nodeStack = new Stack<>();
        this.nodeStack.push(root);
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void postVisit(ASTNode node) {
        if (!(node instanceof ExpressionStatement)) {
            TreeNode treeNode = nodeStack.pop();
            if (treeNode.isLeaf()) {
                treeNode.hash = convertToHex(md5.digest(treeNode.getLabel().getBytes()));
            } else {
                String joined = treeNode.children.stream().map((Function<TreeNode, Object>) treeNode1 -> treeNode1.getASTNode().toString()).map(Object::toString).collect(Collectors.joining());
                treeNode.hash = convertToHex(md5.digest(joined.getBytes()));
            }
        }
    }

    private String convertToHex(final byte[] messageDigest) {
        BigInteger bigint = new BigInteger(1, messageDigest);
        String hexText = bigint.toString(16);
        while (hexText.length() < 32) {
            hexText = "0".concat(hexText);
        }
        return hexText;
    }

    @Override
    public void preVisit(ASTNode node) {
        //Ignore ExpressionStatement.
        if (!(node instanceof ExpressionStatement))
            nodeStack.push(getTreeNode(node));
    }

    @Override
    public boolean visit(QualifiedName node) {
        return false;
    }

    @Override
    public boolean visit(SimpleType node) {
        return false;
    }

    @Override
    public boolean visit(QualifiedType node) {
        return false;
    }

    private TreeNode getTreeNode(ASTNode node) {
        TreeNode treeNode = new TreeNode(getLabel(node), node);
        if (!nodeStack.isEmpty()) {
            nodeStack.peek().addChild(treeNode);
        }
        return treeNode;
    }

    private String getLabel(ASTNode node) {
        String label = node.getClass().getSimpleName();
        if (node instanceof Assignment)
            label += TreeNode.DELIM + ((Assignment) node).getOperator().toString();
        if (node instanceof BooleanLiteral
                || node instanceof Modifier
                || node instanceof SimpleType
                || node instanceof QualifiedType
                || node instanceof PrimitiveType)
            label += TreeNode.DELIM + node.toString();
        if (node instanceof CharacterLiteral)
            label += TreeNode.DELIM + ((CharacterLiteral) node).getEscapedValue();
        if (node instanceof NumberLiteral)
            label += TreeNode.DELIM + ((NumberLiteral) node).getToken();
        if (node instanceof StringLiteral)
            label += TreeNode.DELIM + ((StringLiteral) node).getEscapedValue();
        if (node instanceof InfixExpression)
            label += TreeNode.DELIM + ((InfixExpression) node).getOperator().toString();
        if (node instanceof PrefixExpression)
            label += TreeNode.DELIM + ((PrefixExpression) node).getOperator().toString();
        if (node instanceof PostfixExpression)
            label += TreeNode.DELIM + ((PostfixExpression) node).getOperator().toString();
        if (node instanceof SimpleName)
            label += TreeNode.DELIM + ((SimpleName) node).getIdentifier();
        if (node instanceof QualifiedName)
            label += TreeNode.DELIM + ((QualifiedName) node).getFullyQualifiedName();
        if (node instanceof MethodInvocation)
            label += TreeNode.DELIM + ((MethodInvocation) node).getName().toString();
        if (node instanceof VariableDeclarationFragment)
            label += TreeNode.DELIM + ((VariableDeclarationFragment) node).getName().toString();
        return label;
    }
}
