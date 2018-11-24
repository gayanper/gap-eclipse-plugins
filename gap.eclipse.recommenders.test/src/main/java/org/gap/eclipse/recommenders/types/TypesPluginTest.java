package org.gap.eclipse.recommenders.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.gap.eclipse.recommenders.types.TypesPlugin;
import org.junit.Test;

/**
* Sample integration test. In Eclipse, right-click > Run As > JUnit-Plugin. <br/>
* In Maven CLI, run "mvn integration-test".
*/
public class TypesPluginTest {

	@Test
	public void veryStupidTest() {
		assertEquals("gap.eclipse.recommenders.core",TypesPlugin.PLUGIN_ID);
		assertTrue("Plugin should be started", TypesPlugin.getDefault().started);
	}
}