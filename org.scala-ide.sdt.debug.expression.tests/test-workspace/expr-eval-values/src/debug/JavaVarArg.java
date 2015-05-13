package debug;

public class JavaVarArg {
	private Integer[] args;

	public JavaVarArg(Integer... args) {
		this.args = args;
	}

	public String toString() {
		return "JavaVarArg(" + java.util.Arrays.toString(args) + ")";
	}
}
