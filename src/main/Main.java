package main;

import com.github.gumtreediff.tree.Pair;
import jcodelib.diffutil.TreeDiff;
import jcodelib.parser.TreeBuilder;
import jcodelib.parser.TreeNode;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;


public class Main {
    private static final File changesDir = new File("resources/changes");

    public static void main(String[] args) throws Exception {
        File oldFile = Paths.get(changesDir.getAbsolutePath(), "..", "MyFileBefore.java").toFile();
        File newFile = Paths.get(changesDir.getAbsolutePath(), "..", "MyFileAfter.java").toFile();
        TreeNode oldRoot = TreeBuilder.buildTreeFromFile(oldFile).children.get(0);
        TreeNode newRoot = TreeBuilder.buildTreeFromFile(newFile).children.get(0);


       /* System.out.println(oldRoot.hash);
        System.out.println(newRoot.hash);
        postOrder(oldRoot);
        System.out.println("-----------------------");
        postOrder(newRoot);
        (new MyComparator(oldRoot, newRoot)).compare();*/

        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(oldRoot);
        // breadth first search to make sure no concurrent modification exceptions
        while (!queue.isEmpty()) {
            TreeNode currOldNode = queue.poll();
            if (!currOldNode.isLeaf()) {
                if (pruneTree(currOldNode, newRoot)) {
                    currOldNode.getParent().children.removeIf(node -> node.hash.equals(currOldNode.hash));
                } else {
                    queue.addAll(currOldNode.children);
                }
            }
        }

        // oldRoot and newRoot now only have unmatched subtrees (and leaf nodes as those could easily be wrong matches with hash value)

        System.out.println("done");
    }

    private static void postOrder(TreeNode node) {
        for (TreeNode child : node.children) {
            postOrder(child);
        }
        System.out.println(node.hash);
    }

    private static boolean pruneTree(TreeNode oldNode, TreeNode newRoot) {
        Queue<TreeNode> queue = new LinkedList<>();
        queue.add(newRoot);

        // breadth first search
        while (!queue.isEmpty()) {
            TreeNode temp = queue.poll();
            if (oldNode.hash.equals(temp.hash)) {
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
                queue.addAll(temp.children);
            }
        }
        return false;
    }

    private static void gumTreeForMyFile() throws Exception {
        File oldFile = Paths.get(changesDir.getAbsolutePath(), "..", "MyFileBefore.java").toFile();
        File newFile = Paths.get(changesDir.getAbsolutePath(), "..", "MyFileAfter.java").toFile();
        System.out.println(TreeDiff.diffGumTreeWithGrouping(oldFile, newFile));
    }

    private static TreeNode getTreeForChange(int changeNr) throws IOException {
        String changeDirName = String.format("change%03d", changeNr);
        File old = Objects.requireNonNull((Paths.get(changesDir.getAbsolutePath(), changeDirName, "old").toFile()).listFiles(path -> path.getName().endsWith(".java")))[0];

        return TreeBuilder.buildTreeFromFile(old);
    }

    private static void getGumTreeResultForFile(int changeNr) throws Exception {
        String changeDirName = String.format("change%03d", changeNr);
        File oldFile = Objects.requireNonNull((Paths.get(changesDir.getAbsolutePath(), changeDirName, "old").toFile()).listFiles(path -> path.getName().endsWith(".java")))[0];
        File newFile = Objects.requireNonNull((Paths.get(changesDir.getAbsolutePath(), changeDirName, "new").toFile()).listFiles(path -> path.getName().endsWith(".java")))[0];
        System.out.println(TreeDiff.diffGumTreeWithGrouping(oldFile, newFile));
    }

    private static void gumTreeForOtherFile() throws Exception {
        File oldFile = Paths.get(changesDir.getAbsolutePath(), "..", "DiffTestBefore.java").toFile();
        File newFile = Paths.get(changesDir.getAbsolutePath(), "..", "DiffTestAfter.java").toFile();
        System.out.println(TreeDiff.diffGumTreeWithGrouping(oldFile, newFile));
    }
}

class MyComparator {
    private TreeNode oldRoot;
    private TreeNode newRoot;

    private Stack<Pair<TreeNode, TreeNode>> notEqualNodes = new Stack<>();

    public MyComparator(TreeNode oldRoot, TreeNode newRoot) {
        this.oldRoot = oldRoot.children.get(0);
        this.newRoot = newRoot.children.get(0);
    }

    public void compare() {
        for (TreeNode child : oldRoot.children) {
            if (findInNewTree(child, newRoot)) {
                System.out.println("Found");
            } else {
                System.out.println("Not found: " + child.getASTNode().toString());
            }
        }
    }

    private void processNotEqualNodes() {
        Pair<TreeNode, TreeNode> pair = notEqualNodes.pop();
        TreeNode oldNode = pair.first;
        TreeNode newNode = pair.second;

        List<TreeNode> changes = new ArrayList<>();
        boolean found = false;

        if (oldNode.children.size() < newNode.children.size()) {
            // new file has some inserts
            for (TreeNode newTree : newNode.children) {
                for (TreeNode oldTree : oldNode.children) {
                    if (compNode(newTree, oldTree)) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    changes.add(newTree);
            }
        } else {
            // new file has some deletions
            for (TreeNode oldTree : oldNode.children) {
                for (TreeNode newTree : newNode.children) {
                    if (match(oldTree, newTree)) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    changes.add(oldTree);
            }
        }

        for (TreeNode change : changes) {
            System.out.println(change);
        }
    }

    private boolean compNode(TreeNode a, TreeNode b) {
        //  System.out.println("Comparing \"" + a.getLabel() + " (" + a.children.size() + ")\" with \"" + b.getLabel() + " (" + b.children.size() + ")\"");
        //  return a.getLabel().equals(b.getLabel()) && a.children.size() == b.children.size();
        System.out.println(a.hash + " | " + b.hash);
        return a.getASTNode().toString().equals(b.getASTNode().toString());
    }

    private boolean match(TreeNode target, TreeNode start) {
        // check if nodes match
        if (compNode(target, start)) {
            if (start.isLeaf())
                return target.isLeaf();

            // match their children
            int i = 0;
            for (TreeNode child : start.children) {
                match(target.children.get(i), child);
                ++i;
            }
        } else {
            notEqualNodes.push(new Pair(start, target));
            return false;
        }
        return false;
    }

    private boolean findInNewTree(TreeNode target, TreeNode start) {
        //  if (start.getLabel().equals(target.getLabel())) {
        if (compNode(target, start)) {
            return true;
        } else {
            if (!start.isLeaf()) {
                for (TreeNode child : start.children) {
                    return findInNewTree(target, child);
                }
            } else {
                return false;
            }
        }
        return false;
    }
   /* private TreeNode findInNewTree(TreeNode target, TreeNode startNode) {
        for (TreeNode child : startNode.children) {
            if (child.getLabel().equals(target.getLabel())) {
                System.out.println("FOUND!");
                return child;
            }
            if (child.isLeaf()) return null;

            findInNewTree(target, child);
        }
        return null;
    }*/
}

class PrintVisitor extends ASTVisitor {
    @Override
    public void postVisit(ASTNode node) {
        System.out.println(node);
    }
}
