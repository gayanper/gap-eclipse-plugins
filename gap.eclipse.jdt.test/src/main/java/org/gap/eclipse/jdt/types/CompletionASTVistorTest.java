package org.gap.eclipse.jdt.types;

import static org.gap.eclipse.jdt.ProjectHelper.getCompilationUnit;
import static org.gap.eclipse.jdt.ProjectHelper.getCompletionIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorPart;
import org.gap.eclipse.jdt.ProjectHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("restriction")
public class CompletionASTVistorTest {

	private IJavaProject project;
	private IPackageFragment pkg;

	@Before
	public void before() throws CoreException {
		project = JavaProjectHelper.createJavaProject("TestProject", "bin");
		JavaProjectHelper.addRTJar18(project);
		IPackageFragmentRoot javaSrc = JavaProjectHelper.addSourceContainer(project, "src");
		pkg = javaSrc.createPackageFragment("completion.test", false, null);
	}

	@After
	public void after() throws CoreException {
		JavaProjectHelper.delete(project);
		project = null;
		pkg = null;
	}

	@Test
	public void getExpectedTypes_OnParameter_WithMethodCompletionLambdaExpression_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.concurrent.CompletableFuture;\n");
		code.append("public class ASTFileL {\n");
		code.append("  public String test(String value, int x, java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public CompletableFuture<String> foo() {\n");
		code.append("  	return CompletableFuture.supplyAsync(() -> test(\"test\", 1, java.util.Collections.empty$));\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFileL.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected Type is List", "java.util.List".equals(visitor.getExpectedType().getFullyQualifiedName()));
	}

	@Test
	public void getExpectedTypes_OnParameter_InStreamOnSecondOperation_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class ASTFileL {\n");
		code.append("  public void foo() {\n");
//		code.append(" Stream.of(\"1\").filter(i -> i.isEmpty()).map($)\n");
		code.append(" Stream.of(\"1\").parallel().map($)\n");
//		code.append(" Stream.of($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFileL.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertEquals("Expected Type is List",
				"java.util.function.Function", visitor.getExpectedType().getFullyQualifiedName());
	}

	@Test
	public void getExpectedTypes_MethodCompletionInMethodExpression1_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.*;\n");
		code.append("public class ASTFile {\n");
		code.append("  public int foo() {\n");
		code.append("  	Collections.synchronizedList(Collections.emptyList()).$\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNull("Expected Type is not null", visitor.getExpectedType());
	}

	@Test
	public void getExpectedTypes_MethodCompletionInMethodExpression2_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.*;\n");
		code.append("public class ASTFile {\n");
		code.append("  public int foo() {\n");
		code.append("  	Collections.synchronizedList(Collections.emptyList().$);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected Type is not List",
				"java.util.List".equals(visitor.getExpectedType().getFullyQualifiedName()));
	}

	@Test
	public void getExpectedTypes_OnNonExistingMethodExpression_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.*;\n");
		code.append("public class ASTFile {\n");
		code.append("  public int foo() {\n");
		code.append("  	boo(Collecti$);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNull("Expected Type is not null", visitor.getExpectedType());
	}

	@Test
	public void getExpectedTypes_OnParameter_WithMethodCompletionExpression_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String test(String value, int x, java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String method(String value) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String foo() {\n");
		code.append("  	return method(test(\" \", 10, $));\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected Type is List", "java.util.List".equals(visitor.getExpectedType().getFullyQualifiedName()));
	}

	@Test
	public void getExpectedTypes_Expressing_WithInMethodCompletionExpression_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String test(String value, int x, java.util.List<String> list) {\n");
		code.append("  	return java.util.Collections.emptyList().$;\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNull("Expected Type is not null", visitor.getExpectedType());
	}

	@Test
	public void getExpectedTypes_OnOverloads_OnFirstParameter1_ReturnExpectedTypes() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class ASTFileP1 {\n");
		code.append("  public String test(String value, int x, java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String test(java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String foo() {\n");
		code.append("  	return test($);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected two types", visitor.getExpectedTypes().size() == 2);
		assertTrue("Expected String and List as types",
				visitor.getExpectedTypes().stream().allMatch(t -> t.getFullyQualifiedName().equals("java.lang.String")
						|| t.getFullyQualifiedName().equals("java.util.List")));
	}

	@Test
	public void getExpectedTypes_OnOverloads_OnFirstParameter2_ReturnExpectedTypes() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class ASTFileP2 {\n");
		code.append("  public String test(String value, int x, java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String test(java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String foo() {\n");
		code.append("  	return test(event$);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFileP2.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected two types", visitor.getExpectedTypes().size() == 2);
		assertTrue("Expected String and List as types",
				visitor.getExpectedTypes().stream().allMatch(t -> t.getFullyQualifiedName().equals("java.lang.String")
						|| t.getFullyQualifiedName().equals("java.util.List")));
	}

	@Test
	public void getExpectedTypes_OnOverloads_OnNthParameter_ReturnExpectedTypes() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String test(String value, int x, java.util.Set<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String test(java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String foo() {\n");
		code.append("  	return test(\" \", 1, $);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected two types", visitor.getExpectedTypes().size() == 1);
		assertTrue("Expected Set as type",
				"java.util.Set".equals(visitor.getExpectedTypes().iterator().next().getFullyQualifiedName()));
	}

	@Test
	public void getExpectedTypes_OnParameter_WithChainedMethodExpression1_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.lang.StringBuilder;\n");
		code.append("public class ASTFile1 {\n");
		code.append("  public String foo() {\n");
		code.append("	StringBuilder builder = new StringBuilder();\n");
		code.append("  	builder.append(\"0\").append($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("No multiple expected types", visitor.getExpectedTypes().size() > 1);
	}

	@Test
	public void getExpectedTypes_OnParameter_WithChainedMethodExpression2_ExpectNoErrors() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.lang.StringBuilder;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String foo() {\n");
		code.append("	StringBuilder builder = new StringBuilder();\n");
		code.append("  	builder.appe$nd(\"0\").append(\"a\")");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNull("Expected Type is not null", visitor.getExpectedType());
	}
	
	@Test
	public void getExpectedTypes_OnOverloads_On1stParameterWhileOthersAreFilled_ReturnExpectedType() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String test(java.util.List<String> list) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String test(java.util.List<String> list, int x, java.util.Set<String> set) {\n");
		code.append("  	return null;\n");
		code.append("  }\n");
		code.append("  public String foo() {\n");
		code.append("  	return test($, 1, java.util.Collections.emptySet());\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertNotNull("Expected Type is null", visitor.getExpectedType());
		assertTrue("Expected two types", visitor.getExpectedTypes().size() == 1);
		assertTrue("Expected List as type",
				"java.util.List".equals(visitor.getExpectedTypes().iterator().next().getFullyQualifiedName()));
	}
	
	@Test
	public void getExpectedTypes_InsideLambdaBlock_ExpectZeroTypes() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String foo() {\n");
		code.append("	Stream.of(\"1\").map(s -> {$});\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertTrue("Visitor state is not inside lambda", visitor.isInsideLambda());
		assertTrue("Expected Types are empty", visitor.getExpectedTypes().isEmpty());
	}

	@Test
	public void getExpectedTypes_InSideLambdaStatement_ExpectZeroTypes() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class ASTFile {\n");
		code.append("  public String foo() {\n");
		code.append("	Stream.of(\"1\").map(s -> $);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "ASTFile.java");

		CompletionASTVistor visitor = getVisitedVistor(cu, index);

		assertTrue("Expected Types are not empty", visitor.getExpectedTypes().isEmpty());
	}

	private CompletionASTVistor getVisitedVistor(ICompilationUnit cu, int index) throws Exception {
		IEditorPart editor = EditorUtility.openInEditor(cu);
		ITextViewer viewer = new TextViewer(editor.getSite().getShell(), SWT.NONE);
		viewer.setDocument(new Document(cu.getSource()));
		JavaContentAssistInvocationContext ctx = new JavaContentAssistInvocationContext(viewer, index, editor);
		CompletionASTVistor visitor = new CompletionASTVistor(ctx);

		ProjectHelper.waitForAutoBuild();
		ProjectHelper.waitUntilIndexesReady();

		CompilationUnit ast = CompletionASTVistor.createParsedUnitForCorrectedSource(cu.getElementName(),
				cu.getSource(),
				cu.getJavaProject(), new NullProgressMonitor());
		ast.accept(visitor);
		return visitor;
	}
}
