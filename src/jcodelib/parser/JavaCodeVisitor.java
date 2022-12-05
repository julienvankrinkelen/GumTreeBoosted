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

    public void traversePreOrder(TreeNode newRootNode) {
        // breadth first search to make sure no concurrent modification exceptions
        // first check if root -> then just auto match with other root
        // second check if pruneable
        // check for similarity -> match
        // else TODO check if Update or Remove
        // else add as Delete because no match could be found
        // Lastly, check new Tree for unmatched and add as INSERT
        while (!queue.isEmpty()) {
            currentOldNode = queue.poll();
            if (currentOldNode.isRoot()) {
                queue.addAll(currentOldNode.children);
            } else if (pruneTree(currentOldNode, newRootNode)) {
                System.out.println("removed");

                currentOldNode.getParent().children.removeIf(childNode -> childNode.hash.equals(currentOldNode.hash));
            } else {
                if (!matchWithSimilarity(newRootNode)) {
                    //TODO add OTHER CD CHANGES
                    //CDChange.MOVE CDChange.Update
                    putChangeToResults(CDChange.DELETE, currentOldNode);
                }

                queue.addAll(currentOldNode.children);
            }
        }
        queue.add(newRootNode);
        while (!queue.isEmpty()) {
            TreeNode currentNewNode = queue.poll();
            System.out.println("Nodes Match " + currentNewNode.isMatched());
            if (!currentNewNode.isMatched()) {
                putChangeToResults(CDChange.INSERT, currentNewNode);
            }
            queue.addAll(currentNewNode.children);
        }


        System.out.println("done");
        System.out.println(results);
    }

    private boolean matchWithSimilarity(TreeNode newRootNode) {
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(newRootNode);

        // breadth first search
        while (!queue.isEmpty()) {
            TreeNode currentNewNode = queue.poll();


            if (!currentNewNode.isMatched() && checkSimilarity(currentNewNode)) {
                System.out.println("Similar enough!");

                currentNewNode.setMatched(true);
                currentOldNode.setMatched(true);
                queue.clear();
                return true;
            } else {
                System.out.println("Not similar");
                queue.addAll(currentNewNode.children);
            }
        }
        return false;
    }

    private boolean checkSimilarity(TreeNode currentNewNode) {
        double similarityCoefficient = 0.0;
        double allChildren = currentOldNode.children.size() + currentNewNode.children.size();

        //super confusing way to get different Nodes 
        List<TreeNode> sameChildren = new LinkedList<>(currentOldNode.children);
        List<TreeNode> differentChildren = new LinkedList<>(currentOldNode.children);
        differentChildren.addAll(currentNewNode.children);
        sameChildren.retainAll(differentChildren);
        differentChildren.removeAll(sameChildren);

        if (currentOldNode.getLabel().equals(currentNewNode.getLabel())) similarityCoefficient += 0.25;
        if (currentOldNode.getParent().getLabel().equals(currentNewNode.getParent().getLabel())) similarityCoefficient += 0.25;
        similarityCoefficient += (1 - ((double) differentChildren.size() / allChildren)) / 2;

        return similarityCoefficient >= 0.5;
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
        //TODO remove duplication of breadth first search
        while (!queue.isEmpty()) {
            TreeNode currentNewNode = queue.poll();
            queue.addAll(currentNewNode.children);

            if (checkIfNotMatchedAndSameHash(oldNode, currentNewNode)) {
                System.out.println("Hash equal!");
                // same for new tree
                currentNewNode.getParent().children.removeIf(node -> node.hash.equals(currentNewNode.hash));

                // algorithm done, exit while loop
                queue.clear();
                return true;
            } else {
                System.out.println("Hashes not equal, need to check children");
            }
        }
        return false;

    }

    private boolean checkIfNotMatchedAndSameHash(TreeNode oldNode, TreeNode temp) {
        //checks if node is already matched and checks if node has hash - normally only root does not have hash
        return !temp.isMatched() && oldNode.hash != null && temp.hash != null && oldNode.hash.equals(temp.hash);
    }

    private void putChangeToResults(String changeType, TreeNode node) {
        String nodeType = node.getLabel();
        int startingPosition = node.getASTNode().getStartPosition();
        int endPosition = startingPosition + node.getASTNode().getLength();
        CDChange change = new CDChange(changeType, nodeType, startingPosition, endPosition);

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
