package jcodelib.diffutil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;

import edu.fdu.se.cldiff.CLDiffLocal;
import file.FileIOManager;
import jcodelib.element.CDChange;
import jcodelib.element.GTAction;
import jcodelib.util.CodeUtils;
import script.ScriptGenerator;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;
import tree.TreeNode;

public class TreeDiff {
	public static void updateEntityTypes(File leftFile, File rightFile, List<CDChange> changes) throws IOException {
		//Create maps for start/end positions.
		Tree left = TreeBuilder.buildTreeFromFile(leftFile);
		Tree right = TreeBuilder.buildTreeFromFile(rightFile);
		Map<Integer, TreeMap<Integer, String>> leftTypeMap = createNodeTypeMap(left.getRoot());
		Map<Integer, TreeMap<Integer, String>> rightTypeMap = createNodeTypeMap(right.getRoot());

		//Search for corresponding TreeNode using start/end positions.
		for(CDChange c : changes) {
			//Check left or right based on change type. If missing, use Unknown# + original.
			String entityType = null;
			switch(c.getChangeType()) {
			case CDChange.INSERT:
				entityType = findClosestEntity(rightTypeMap, c.getStartPos(), c.getEndPos());
				break;
			case CDChange.DELETE:
			case CDChange.MOVE:
			case CDChange.UPDATE:
				entityType = findClosestEntity(leftTypeMap, c.getStartPos(), c.getEndPos());
			}
			c.setEntityType(entityType == null ? "Unknown#"+c.getEntityType() : entityType);
		}
	}

	private static String findClosestEntity(Map<Integer, TreeMap<Integer, String>> map, int start, int end) {
		//Find an entity which has the same start position and closest end position.
		if(!map.containsKey(start))
			return null;
		Integer closest = map.get(start).ceilingKey(end);
		return closest != null ? map.get(start).get(closest) : null;
	}

	private static Map<Integer, TreeMap<Integer, String>> createNodeTypeMap(TreeNode n) {
		Map<Integer, TreeMap<Integer, String>> map = new HashMap<>();
		createNodeTypeMap(map, n);
		return map;
	}

	private static void createNodeTypeMap(Map<Integer, TreeMap<Integer, String>> map, TreeNode n) {
		if(n != null && n.getASTNode() != null) {
			int start = n.getStartPosition();
			int end = n.getEndPosition();
			if(!map.containsKey(start))
				map.put(start, new TreeMap<>());
			map.get(start).put(end, CodeUtils.getTypeName(n.getType()));
		}
		for(TreeNode child : n.children) {
			createNodeTypeMap(map, child);
		}
	}

	public static List<com.github.gumtreediff.actions.model.Action> diffGumTree(File srcFile, File dstFile) throws Exception {
		List<com.github.gumtreediff.actions.model.Action> actions = null;
		com.github.gumtreediff.client.Run.initGenerators();
		ITree src = Generators.getInstance().getTree(srcFile.getAbsolutePath()).getRoot();
		ITree dst = Generators.getInstance().getTree(dstFile.getAbsolutePath()).getRoot();
		Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
		m.match();
		ActionGenerator g = new ActionGenerator(src, dst, m.getMappings());
		g.generate();
		actions = g.getActions();

		return actions;
	}

	public static List<GTAction> groupGumTreeActions(File srcFile, File dstFile, List<com.github.gumtreediff.actions.model.Action> actions) {
		List<GTAction> gtActions = new ArrayList<>();
		try {
			CompilationUnit srcCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(srcFile));
			CompilationUnit dstCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(dstFile));
			//Group actions.
			while(actions.size() > 0){
				Action action = actions.get(0);
				GTAction gtAction = new GTAction(action, srcCu, dstCu);
				gtAction = attachActions(gtAction, actions, srcCu, dstCu);
				gtActions.add(gtAction);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return gtActions;
	}

	public static List<GTAction> diffGumTreeWithGrouping(File srcFile, File dstFile) throws Exception {
		List<GTAction> gtActions = new ArrayList<>();
		com.github.gumtreediff.client.Run.initGenerators();
		ITree src = Generators.getInstance().getTree(srcFile.getAbsolutePath()).getRoot();
		ITree dst = Generators.getInstance().getTree(dstFile.getAbsolutePath()).getRoot();
		Matcher m = Matchers.getInstance().getMatcher(src, dst); // retrieve the default matcher
		m.match();
		ActionGenerator g = new ActionGenerator(src, dst, m.getMappings());
		g.generate();
		List<com.github.gumtreediff.actions.model.Action> actions = g.getActions();
		CompilationUnit srcCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(srcFile));
		CompilationUnit dstCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(dstFile));
		//Group actions.
		while(actions.size() > 0){
			Action action = actions.get(0);
			GTAction gtAction = new GTAction(action, srcCu, dstCu);
			gtAction = attachActions(gtAction, actions, srcCu, dstCu);
			gtActions.add(gtAction);
		}
		return gtActions;
	}

	private static GTAction attachActions(
			GTAction gtAction, List<Action> actions, CompilationUnit srcCu, CompilationUnit dstCu) {
		GTAction root = gtAction;

		//Bottom-up search to find a root action.
		GTAction parent;
		ITree parentNode;
		do {
			parent = null;
			parentNode = root.action.getNode().getParent();
			if (parentNode != null) {
				for (Action action : actions) {
					if (action.getNode().getId() == parentNode.getId()
							&& GTAction.getActionType(action).equals(root.actionType)) {
						parent = new GTAction(action, srcCu, dstCu);
						break;
					}
				}
			}
			//Switch the root.
			if(parent != null){
				root = parent;
			}
		} while (parent != null);

		//Top-down search for children.
		List<GTAction> targetActions = new ArrayList<>();
		List<GTAction> attachedActions = new ArrayList<>();
		targetActions.add(root);
		actions.remove(root.action);
		do {
			targetActions.addAll(attachedActions);
			attachedActions.clear();
			//Find children of each target.
			for(GTAction target : targetActions){
				for (ITree child : target.action.getNode().getChildren()) {
					for (Action action : actions) {
						if (action.getNode().getId() == child.getId()
								&& target.actionType.equals(GTAction.getActionType(action))) {
							GTAction gta = new GTAction(action, srcCu, dstCu);
							target.children.add(gta);
							attachedActions.add(gta);
							break;
						}
					}
				}
			}
			//Remove all attached actions.
			for(GTAction gta : attachedActions){
				actions.remove(gta.action);
			}
		} while (attachedActions.size() > 0 && actions.size() > 0);

		return root;
	}

	public static EditScript diffLAS(File srcFile, File dstFile){
		try {
			Tree before = tree.TreeBuilder.buildTreeFromFile(srcFile);
			Tree after = tree.TreeBuilder.buildTreeFromFile(dstFile);
			EditScript script = ScriptGenerator.generateScript(before, after);
			return script;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public static void runCLDiff(File srcFile, File dstFile) {

	}

	public static void runCLDiff(String repo, String commitId, String outputDir) {
		CLDiffLocal CLDiffLocal = new CLDiffLocal();
		CLDiffLocal.run(commitId,repo,outputDir);
	}
}
