package org.gap.eclipse.jdt;

import org.gap.eclipse.jdt.common.SignaturesAssignableTest;
import org.gap.eclipse.jdt.types.CompletionASTVistorTest;
import org.gap.eclipse.jdt.types.Java8ProposalComputerTest;
import org.gap.eclipse.jdt.types.Java8ProposalComputer_StreamTest;
import org.gap.eclipse.jdt.types.SmartEnumLiteralProposalComputerTest;
import org.gap.eclipse.jdt.types.SmartSubTypeComputerTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ CompletionASTVistorTest.class, Java8ProposalComputer_StreamTest.class, Java8ProposalComputerTest.class,
		SmartEnumLiteralProposalComputerTest.class, SmartSubTypeComputerTest.class, SignaturesAssignableTest.class })
public class AllTests {

}
