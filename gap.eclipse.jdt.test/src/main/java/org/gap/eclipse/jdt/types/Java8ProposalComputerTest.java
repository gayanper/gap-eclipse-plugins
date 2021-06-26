package org.gap.eclipse.jdt.types;

import static org.gap.eclipse.jdt.ProjectHelper.getCompilationUnit;
import static org.gap.eclipse.jdt.ProjectHelper.getCompletionIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Java8ProposalComputerTest extends ComputerTestBase {
	
	@Before
	public void before() throws CoreException {
		setupProject(new Java8ProposalComputer());
	}

	@After
	public void after() throws CoreException {
		disposeProject();
	}

	@Test
	public void compute_LambdaSuggestion_On_FunctionalTypeParameter() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");
		
		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index).stream().map(this::convert)
				.sorted(this::compare).limit(2).collect(Collectors.toList());
		
		List<String> expected = Arrays.asList("(.) ->", "(.) -> {}");
		
		List<String> actual = completions.stream().map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestionWithSameClassMethodReferences_On_FunctionalTypeParameter() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  private boolean isEmpty(String value) { \n");
		code.append("  	 return false;");
		code.append("  }\n");
		code.append("  protected boolean isEmptyProt(String value) { \n");
		code.append("  	 return false;");
		code.append("  }\n");
		code.append("  public boolean isEmptyPub(String value) { \n");
		code.append("  	 return false;");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("(.) ->", "(.) -> {}", "this::isEmptyPub", "this::isEmptyProt",
				"this::isEmpty", "Objects::isNull", "Objects::nonNull");

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_Filter_LambdaSuggestion_On_FunctionalTypeParameter() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  private boolean isEmpty(String value) { \n");
		code.append("  	 return false;");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(isEmp$)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("this::isEmpty");
		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).limit(1).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_OnPredicate_SuggestObjects() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("(.) ->", "(.) -> {}", "Objects::isNull", "Objects::nonNull");
		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_ShouldApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test($)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		Optional<LazyJavaCompletionProposal> completion = computeCompletionProposals(cu, index).stream()
				.map(this::convert).sorted(this::compare).findFirst();

		assertTrue("No completion found", completion.isPresent());

		String actual = computeActual(completion.get(), cu, index);
		String expected = computeExpected(code, "$", "arg0 -> ");

		assertEquals("(.) -> was not applied correctly", expected, actual);
	}

	@Test
	public void compute_LambdaSuggestionWithoutParamsBlock_ShouldApply() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.security.AccessController;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("    AccessController.doPrivileged($);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		String actual = computeActual(completions.get(1), cu, index);
		String expected = computeExpected(code, "$", "() -> {}");

		assertEquals("(.) -> was not applied correctly", expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_MethodRef_ShouldAppy() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  private boolean isEmpty(String value) { \n");
		code.append("  	 return false;");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(isEmp$)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		String actual = computeActual(completions.get(0), cu, index);
		String expected = computeExpected(code, "isEmp$", "this::isEmpty");

		assertEquals("this::isEmpty was not applied correctly", expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_On_FunctionalTypeVariable() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  private java.util.function.Supplier provider = $\n");
		code.append("  public void foo() {\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("() ->", "() -> {}");

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_InSideLambdaExpression() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(i -> $)\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), Collections.emptyList(), actual);
	}

	@Test
	public void compute_LambdaSuggestion_InSideLambdaBlock() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public void test(java.util.function.Predicate<String> p) {\n");
		code.append("  }\n");
		code.append("  public void foo() {\n");
		code.append("    test(i -> { return $})\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), Collections.emptyList(), actual);
	}

	@Test
	public void compute_UniqueLambdaSuggestion_On_OverloadedMethods() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.concurrent.CompletableFuture;\n");
		code.append("public class Java8 {\n");
		code.append("  public void foo() {\n");
		code.append("		CompletableFuture.supplyAsync($)");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index);

		List<String> expected = Arrays.asList("() ->", "() -> {}");

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
	}

	@Test
	public void compute_LambdaSuggestion_On_NonFunctionalTypeParameter() throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("public class Java8 {\n");
		code.append("  public static void foo() {\n");
		code.append("		boo($);\n");
		code.append("  }\n");
		code.append("  public static void boo(Integer i) {\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index, 4);

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(String.format("No type is expected : %s", actual.toString()), 0, actual.size());
	}

	@Test
	public void compute_WhenFirstOverloadedMethodHasNoParameters_ExpectForOverloadedMethodParameters()
			throws Exception {
		StringBuilder code = new StringBuilder();
		code.append("package completion.test;\n");
		code.append("import java.util.stream.Stream;");
		code.append("public class Java8 {\n");
		code.append("  public static void foo() {\n");
		code.append("		Stream.of(1,2,3).sorted($);\n");
		code.append("  }\n");
		code.append("}\n");

		int index = getCompletionIndex(code);
		ICompilationUnit cu = getCompilationUnit(pkg, code, "Java8.java");
		List<ICompletionProposal> completions = computeCompletionProposals(cu, index, 4);

		List<String> expected = Arrays.asList("(..) ->", "(..) -> {}");

		List<String> actual = completions.stream().map(this::convert).sorted(this::compare)
				.map(c -> c.getDisplayString()).collect(Collectors.toList());
		assertEquals(actual.toString(), expected, actual);
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
