package org.gap.eclipse.jdt.symbol;

import java.util.Comparator;

import org.eclipse.jdt.core.IMember;

public class SymbolComparator implements Comparator<IMember> {

	@Override
	public int compare(IMember o1, IMember o2) {
		return o1.getElementName().compareTo(o2.getElementName());
	}

}
