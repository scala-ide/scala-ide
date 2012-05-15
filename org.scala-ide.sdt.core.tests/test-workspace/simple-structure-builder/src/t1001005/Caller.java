package t1001005;

import java.io.IOException;
import java.net.SocketException;

public class Caller {
	public static void main(String[] args) {
		try {
			try {
				new Callee().doStuff(Integer.parseInt(args[0]));
			} 
			catch (InstantiationException e) {}

			try {
				new Callee(2).doStuff(Integer.parseInt(args[0]));
			}
			catch (NoSuchFieldException e) {}
		}
		catch (SocketException e) {} 
		catch (IOException e) {}
	}
}