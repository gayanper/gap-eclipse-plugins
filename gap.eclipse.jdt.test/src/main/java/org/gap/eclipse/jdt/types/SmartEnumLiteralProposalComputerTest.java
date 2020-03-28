package org.gap.eclipse.jdt.types;

import static org.gap.eclipse.jdt.ProjectHelper.getCompilationUnit;
import static org.gap.eclipse.jdt.ProjectHelper.getCompletionIndex;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IEditorPart;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("restriction")
public class SmartEnumLiteralProposalComputerTest {
	
	private IJavaProject project;
	private IPackageFragmentRoot javaSrc;
	private IPackageFragment pkg;

	@Before
	public void before() throws CoreException {
		project= JavaProjectHelper.createJavaProject("TestProject", "bin");
		JavaProjectHelper.addRTJar18(project);
		javaSrc= JavaProjectHelper.addSourceContainer(project, "src");
		pkg= javaSrc.createPackageFragment("completion.test", false, null);
	}

	@After
	public void after() throws CoreException {
		JavaProjectHelper.delete(project);
	}

	@Test
	public void compute_EnumLiterals_On_EnumVariableAssignment() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test() {\n");
		code.append("    java.lang.Thread.State state = $\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}
	
	@Test
	public void compute_EnumLiterals_On_FirstEnumParameter() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_SecondEnumParameter() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(String p1, java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(\"\",$)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_SecondEnumParameterPrecedeWithSpace() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(String p1, java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(\"\", $)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_VarArgs_FirstValue() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State... state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_VarArgs_NthValue() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State... state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(Thread.State.WAITING, $)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_Overloaded_Methods() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void test(java.nio.file.AccessMode mode) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = new ArrayList<>(expectedCompletions());
		expected.add("READ : AccessMode - java.nio.file.AccessMode");
		expected.add("WRITE : AccessMode - java.nio.file.AccessMode");
		expected.add("EXECUTE : AccessMode - java.nio.file.AccessMode");
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_Overloaded_Methods_OneParamMethod() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void test(String name, java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_Overloaded_Methods_TwoParamMethod() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void test(String name, java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(\" \", $)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_Overloaded_Methods_PrivateMethods_SameClass() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void test(java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  private void test(String name, java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(\" \", $)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	@Test
	public void compute_EnumLiterals_On_Overloaded_Methods_PrivateMethods_DifferentClass() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class SmartStatic {\n");
		code.append("  public static void test(java.lang.Thread.State state) {\n");
		code.append("  }\n");
		code.append("  private static void test(java.nio.file.AccessMode mode) {\n");
		code.append("  }\n");
		code.append("public class SmartEnumLiteral {\n");
		code.append("  public void foo() {\n");
		code.append("    SmartStatic.test(\" \", $)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SmartEnumLiteral.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = expectedCompletions();
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertTrue(actual.toString() ,expected.containsAll(actual));
	}

	private List<String> expectedCompletions() {
		return Arrays.asList("NEW : Thread.State - java.lang.Thread.Thread.State", 
				"RUNNABLE : Thread.State - java.lang.Thread.Thread.State", 
				"BLOCKED : Thread.State - java.lang.Thread.Thread.State", 
				"WAITING : Thread.State - java.lang.Thread.Thread.State",
				"TIMED_WAITING : Thread.State - java.lang.Thread.Thread.State", 
				"TERMINATED : Thread.State - java.lang.Thread.Thread.State");
	}
	
	private List<ICompletionProposal> computeCompletionProposals(ICompilationUnit cu, int completionIndex) throws Exception {
		SmartEnumLiteralProposalComputer comp= new SmartEnumLiteralProposalComputer();

		IEditorPart editor= EditorUtility.openInEditor(cu);
		ITextViewer viewer= new TextViewer(editor.getSite().getShell(), SWT.NONE);
		viewer.setDocument(new Document(cu.getSource()));
		JavaContentAssistInvocationContext ctx= new JavaContentAssistInvocationContext(viewer, completionIndex, editor);

		return comp.computeCompletionProposals(ctx, null);
	}
	
}
