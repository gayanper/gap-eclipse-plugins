package org.gap.eclipse.jdt.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jdt.core.Signature;
import org.junit.Test;

public class SignaturesAssignableTest {
	
	@Test
	public void testSameNoneParamType() {
		String sig = Signature.createTypeSignature("java.lang.List", true);
		assertTrue("Signatures are not assignale", Signatures.isAssignable(sig, sig));
	}

	@Test
	public void testSameGenericType() {
		String sig = Signature.createTypeSignature("java.lang.List<String>", true);
		assertTrue("Signatures are not assignale", Signatures.isAssignable(sig, sig));
	}

	@Test
	public void testSameParameterizeGenericType() {
		String toSig = Signature.createTypeSignature("java.lang.List<String>", true);
		String sig = Signature.createTypeSignature("java.lang.List<E>", true);

		assertTrue("Signatures are not assignale", Signatures.isAssignable(sig, toSig));
	}

	@Test
	public void testSameParameterizeUpperBoundGenericType() {
		String toSig = Signature.createTypeSignature("java.lang.List<String>", true);
		String sig = Signature.createTypeSignature("java.lang.List<? extends E>", true);

		assertTrue("Signatures are not assignale", Signatures.isAssignable(sig, toSig));
	}

	@Test
	public void testDifferentNoneParamType() {
		String toSig = Signature.createTypeSignature("java.lang.List", true);
		String sig = Signature.createTypeSignature("java.lang.Set", true);
		assertFalse("Signatures are not assignale", Signatures.isAssignable(sig, toSig));
	}

	@Test
	public void testDifferentGenericType() {
		String toSig = Signature.createTypeSignature("java.lang.List<String>", true);
		String sig = Signature.createTypeSignature("java.lang.Set<String>", true);
		assertFalse("Signatures are not assignale", Signatures.isAssignable(sig, toSig));
	}

	@Test
	public void testDifferentParameterizeGenericType() {
		String toSig = Signature.createTypeSignature("java.lang.Set<String>", true);
		String sig = Signature.createTypeSignature("java.lang.List<E>", true);

		assertFalse("Signatures are not assignale", Signatures.isAssignable(sig, toSig));
	}

	@Test
	public void testDifferentParameterizeUpperBoundGenericType() {
		String toSig = Signature.createTypeSignature("java.lang.Set<String>", true);
		String sig = Signature.createTypeSignature("java.lang.List<? extends E>", true);

		assertFalse("Signatures are not assignale", Signatures.isAssignable(sig, toSig));
	}


}
