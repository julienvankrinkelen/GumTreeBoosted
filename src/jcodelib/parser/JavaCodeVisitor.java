package jcodelib.parser;

import jcodelib.element.CDChange;
import org.eclipse.jdt.core.dom.*;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JavaCodeVisitor extends ASTVisitor {

    private final MessageDigest md5;
    private Stack<TreeNode> nodeStack;
    HashMap<CDChange, LinkedList<TreeNode>> results = new HashMap<>();
    Queue<TreeNode> queue = new LinkedList<>();
    private TreeNode currentOldNode;

    public JavaCodeVisitor(TreeNode root) {
        this.nodeStack = new Stack<>();
        this.nodeStack.push(root);
        queue.add(nodeStack.peek());

        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void traversePreOrder(TreeNode node) {
        // breadth first search to make sure no concurrent modification exceptions
        while (!queue.isEmpty()) {
            currentOldNode = queue.poll();
            if (currentOldNode.isRoot()) {
                queue.addAll(currentOldNode.children);
            } else if (!currentOldNode.isLeaf()) {

                if (pruneTree(currentOldNode, node)) {
                    System.out.println("removed");

                    currentOldNode.getParent().children.removeIf(childNode -> childNode.hash.equals(currentOldNode.hash));
                } else {
                    String nodeType = currentOldNode.getLabel();
                    int startingPosition = currentOldNode.getASTNode().getStartPosition();
                    int endPosition = startingPosition + currentOldNode.getASTNode().getLength();


                    if (!matchWithSimilarity(node)) {
                        //TODO add OTHER CD CHANGES
                        putChangeToResults(new CDChange(CDChange.DELETE, nodeType, startingPosition, endPosition), currentOldNode);
                    }


                    queue.addAll(currentOldNode.children);
                }
            }
        }

        System.out.println("done");
        System.out.println(results);
    }

    private boolean matchWithSimilarity(TreeNode node) {
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(node);

        // breadth first search
        while (!queue.isEmpty()) {
            TreeNode temp = queue.poll();


            if (!temp.isMatched() && checkSimilarity(temp)) {
                System.out.println("Similar enough!");

                temp.isMatched();
                currentOldNode.isMatched();
                queue.clear();
                return true;
            } else {
                System.out.println("Not similar");
                queue.addAll(temp.children);
            }
        }
        return false;
    }

    private boolean checkSimilarity(TreeNode node) {

        double similarityCoefficient = 0.0;
        double allChildren = currentOldNode.children.size() + node.children.size();

        List<TreeNode> sameChildren = new ArrayList<>(currentOldNode.children);
        List<TreeNode> differentChildren = new ArrayList<>(currentOldNode.children);
        differentChildren.addAll(node.children);
        sameChildren.retainAll(differentChildren);
        differentChildren.removeAll(sameChildren);

        if (currentOldNode.getLabel().equals(node.getLabel())) similarityCoefficient += 0.25;
        if (currentOldNode.getParent() == node.getParent()) similarityCoefficient += 0.25;
        similarityCoefficient += (1 - ((double) differentChildren.size() / allChildren)) / 2;
        System.out.println(similarityCoefficient);
        return similarityCoefficient > 0.5;
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

    public boolean visit(ASTNode node) {
        ASTMatcher matcher = new ASTMatcher();
        boolean result = node.subtreeMatch(matcher, node);
        return result;
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

    private boolean pruneTree(TreeNode oldNode, TreeNode newRoot) {
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(newRoot);

        // breadth first search
        while (!queue.isEmpty()) {
            TreeNode temp = queue.poll();
            queue.addAll(temp.children);

            if (!temp.isMatched() && oldNode.hash.equals(temp.hash)) {
                System.out.println("Hash equal!");
                // TODO: special case if no changes, then root node and it has no parent!!
                // remove oldNode from parent's children list
                // oldNode.getParent().children.removeIf(node -> node.hash.equals(oldNode.hash));

                // same for new tree
                temp.getParent().children.removeIf(node -> node.hash.equals(temp.hash));

                // algorithm done, exit while loop
                queue.clear();
                return true;
            } else {
                System.out.println("Hashes not equal, need to check children");
            }
        }
        return false;

    }

    private void putChangeToResults(CDChange change, TreeNode node) {
        LinkedList<TreeNode> existingList = results.get(change);
        if (existingList != null) {
            existingList.add(node);

        } else { // No such key
            LinkedList<TreeNode> firstEntry = new LinkedList<>();
            firstEntry.add(node);
            results.put(change, firstEntry);
        }
    }

    //more of a set treenode?
    private TreeNode getTreeNode(ASTNode node) {
        TreeNode treeNode = new TreeNode(getLabel(node), node, false);
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
