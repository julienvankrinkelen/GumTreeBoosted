package main;

import jcodelib.diffutil.TreeDiff;
import jcodelib.parser.TreeBuilder;
import jcodelib.parser.TreeNode;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;

public class Main {
    private static final File changesDir = new File("resources/changes");

    public static void main(String[] args) throws IOException {
        TreeNode root = getTreeForChange(3);
        root.children.get(0).getASTNode().accept(new PrintVisitor());
        //.children.get(0).children.forEach(child -> System.out.println(child.getLabel()));
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
}

class PrintVisitor extends ASTVisitor {
    @Override
    public void postVisit(ASTNode node) {
        super.postVisit(node);
        System.out.println(node);
    }
}
