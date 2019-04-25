package org.expresso;

import org.expresso.java.FunctionalJavaTest;
import org.expresso.java.OrderOfOperationsTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/** All tests in Expresso */
@RunWith(Suite.class)
@SuiteClasses({ //
	FunctionalJavaTest.class, //
	OrderOfOperationsTest.class//
})
public class ExpressoTests {
}
