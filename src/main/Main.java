package main;

import com.github.gumtreediff.tree.Pair;
import jcodelib.diffutil.TreeDiff;
import jcodelib.parser.JavaCodeVisitor;
import jcodelib.parser.TreeBuilder;
import jcodelib.parser.TreeNode;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;


public class Main {
    private static final File changesDir = new File("resources/changes");

    public static void main(String[] args) throws Exception {
        File oldFile = Paths.get(changesDir.getAbsolutePath(), "..", "MyFileBefore.java").toFile();
        File newFile = Paths.get(changesDir.getAbsolutePath(), "..", "MyFileAfter.java").toFile();

        TreeNode oldRoot = TreeBuilder.buildTreeFromFile(oldFile).children.get(0);
        TreeNode newRoot = TreeBuilder.buildTreeFromFile(newFile).children.get(0);

        TreeNode rootOld = TreeBuilder.buildTreeFromFile(oldFile);
        TreeNode rootNew = TreeBuilder.buildTreeFromFile(newFile);


        JavaCodeVisitor javaCodeVisitor = new JavaCodeVisitor(rootOld);

        javaCodeVisitor.traversePreOrder(rootNew);

    }


    private static void postOrder(TreeNode node) {
        for (TreeNode child : node.children) {
            postOrder(child);
        }
        System.out.println(node.hash);
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
