package org.gap.eclipse.jdt.types;

import static org.gap.eclipse.jdt.ProjectHelper.getCompilationUnit;
import static org.gap.eclipse.jdt.ProjectHelper.getCompletionIndex;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Java8ProposalComputer_StreamTest extends ComputerTestBase {
	
	@Before
	public void before() throws CoreException {
		setupProject(new Java8ProposalComputer());
	}

	@After
	public void after() throws CoreException {
		disposeProject();
	}

	@Test
	public void compute_LambdaSuggestion_OnStreams_TerminalOpt() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    Stream.of(\"1\").forEach($)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);
		
		List<String> expected = Arrays.asList("(.) ->", "(.) -> {}");
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_OnStreams_TerminalOpt_ShouldaApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    Stream.of(\"1\").forEach($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		String actual = computeActual(completions.stream().map(this::convert).sorted(this::compare).findFirst().get(),
				cu, index);
		String expected = computeExpected(code, "$", "arg0 -> ");

		assertEquals("(.) -> was not applied correctly", expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_OnStreams_NonTerminalOpt() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    Stream.of(\"1\").filter($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("(.) ->", "(.) -> {}");

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).limit(2).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_OnStreams_NonTerminalOpt_ShouldaApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    Stream.of(\"1\").filter($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		String actual = computeActual(completions.stream().map(this::convert).sorted(this::compare).findFirst().get(),
				cu, index);
		String expected = computeExpected(code, "$", "arg0 -> ");

		assertEquals("(.) -> was not applied correctly", expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_OnStreams_SecondNonTerminalOpt() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    Stream.of(\"1\").filter(i -> i.isEmpty()).map($);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("(.) ->", "(.) -> {}");

		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_OnStreams_SecondNonTerminalOpt_ShouldaApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    Stream.of(\"1\").filter(i -> i.isEmpty()).map($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		String actual = computeActual(completions.stream().map(this::convert).sorted(this::compare).findFirst().get(),
				cu, index);
		String expected = computeExpected(code, "$", "arg0 -> ");

		assertEquals("(.) -> was not applied correctly", expected, actual);
	}

	private LazyJavaCompletionProposal convert(ICompletionProposal proposal) {
		return (LazyJavaCompletionProposal) proposal;
	}

	private int compare(LazyJavaCompletionProposal left, LazyJavaCompletionProposal right) {
		// descending order sorting
		if(left.getRelevance() == right.getRelevance()) {
			return 0;
		} else if (left.getRelevance() < right.getRelevance()) {
			return 1;
		} else {
			return -1;
		}
	}
}
