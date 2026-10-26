package ztest;

public class TestString {

	public static void main(String[] args) {
		String[] strs = new String[] {
			"QSALES3RD", "QSALESIC", "QSALESAS", "QPROD", "QAS", "QPURCHIC", "QPURCH3RD"
		};
		for (String str : strs) {
			System.out.println(str.replace("3RD", "THIRD"));
		}

	}

}



