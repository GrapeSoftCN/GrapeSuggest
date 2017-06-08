package Test;

import httpServer.booter;

public class TestSuggest {
	public static void main(String[] args) {
		booter booter = new booter();
		 try {
		 System.out.println("GrapeSuggest!");
		 System.setProperty("AppName", "GrapeSuggest");
		 booter.start(1003);
		 } catch (Exception e) {
		 }
	}
}
