package main;

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
}

class PrintVisitor extends ASTVisitor {
    @Override
    public void postVisit(ASTNode node) {
        super.postVisit(node);
        System.out.println(node);
    }
}
