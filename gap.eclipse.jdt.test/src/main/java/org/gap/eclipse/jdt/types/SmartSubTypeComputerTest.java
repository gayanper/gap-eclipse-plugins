package org.gap.eclipse.jdt.types;

import static org.gap.eclipse.jdt.ProjectHelper.getCompilationUnit;
import static org.gap.eclipse.jdt.ProjectHelper.getCompletionIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SmartSubTypeComputerTest extends ComputerTestBase {
	
	@Before
	public void before() throws CoreException {
		setupProject(new SmartTypeProposalComputer());
	}

	@After
	public void after() throws CoreException {
		disposeProject();
	}

	@Test
	public void compute_SubType_ForVariable() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.List;\n");
		code.append("public class SubType {\n");
		code.append("  public void foo() {\n");
		code.append("    List<String> list = new $");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SubType.java");
		computeCompletionProposals(cu, index);
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		
		assertTrue(actual.toString(), actual.contains("ArrayList(int arg0) - java.util.ArrayList"));
	}

	@Test
	public void compute_SubType_ForMethodInvocation() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.List;\n");
		code.append("public class SubType {\n");
		code.append("  public void foo() {\n");
		code.append("		boo(new $);");
		code.append("  }\n");
		code.append("  public void boo(List<String> list) {\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "SubType.java");
		computeCompletionProposals(cu, index);
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());

		assertTrue(actual.toString(), actual.contains("ArrayList(int arg0) - java.util.ArrayList"));
	}

	@Test
	public void compute_SubType_ForVariable_ShouldApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("//import;\n");
		code.append("import java.util.List;\n");
		code.append("public class SubType {\n");
		code.append("  public void foo() {\n");
		code.append("    List<String> list = new $\n");
		code.append("  }\n");
		code.append("}\n");

		StringBuilder src = withoutImport(code);
		int index = getCompletionIndex(src);
		ICompilationUnit cu = getCompilationUnit(pkg, src, "SubType.java");
		computeCompletionProposals(cu, index);
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		Optional<ICompletionProposal> completion = completions.stream()
				.filter(c -> c.getDisplayString().equals("ArrayList(int arg0) - java.util.ArrayList")).findFirst();

		assertTrue("No completion found", completion.isPresent());
		String actual = computeActual(completion.get(), cu, index);
		String expected = computeExpected(code, "$", "ArrayList<>(0)", "java.util.ArrayList");

		assertEquals("Completion was not applied correctly", expected, actual);
	}

	@Test
	public void compute_SubType_ForMethodInvocation_ShouldApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("//import;\n");
		code.append("import java.util.List;\n");
		code.append("public class SubType {\n");
		code.append("  public void foo() {\n");
		code.append("		boo(new $);\n");
		code.append("  }\n");
		code.append("  public void boo(List<String> list) {\n");
		code.append("  }\n");
		code.append("}\n");

		StringBuilder src = withoutImport(code);
		int index = getCompletionIndex(src);
		ICompilationUnit cu = getCompilationUnit(pkg, src, "SubType.java");
		computeCompletionProposals(cu, index);
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		Optional<ICompletionProposal> completion = completions.stream()
				.filter(c -> c.getDisplayString().equals("ArrayList(int arg0) - java.util.ArrayList")).findFirst();

		assertTrue("No completion found", completion.isPresent());
		String actual = computeActual(completion.get(), cu, index);
		String expected = computeExpected(code, "$", "ArrayList<>(0)", "java.util.ArrayList");

		assertEquals("Completion was not applied correctly", expected, actual);
	}
}
