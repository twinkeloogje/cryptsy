package be.twinkel.cryptsy.demo;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
//import java.util.Date;
//import java.util.Locale;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;

import be.twinkel.cryptsy.core.CryptsyApi;
import be.twinkel.cryptsy.treemodel.OrderBook;

public class CryptsyTicker {
	private static final String[] PAIRS={"3"};
	private static CryptsyApi capi;
	
	public static final int ASK=0;
	public static final int BID=1;
	public static final int PRICE=OrderBook.PRICE;
	public static final int VOL=OrderBook.VOL;
	
	
	

	static {
		Properties cred=new Properties();

		try {
			cred.load(new FileInputStream("resources/credentials.properties"));
		} catch (FileNotFoundException fnfe){
			System.err.println("ERR-0001 : Trader init is missing credentials file : resources/credentials.properties");
			System.exit(-1);
		} catch (IOException ioe){
			System.err.println("ERR-0002 : Trader init couldn't read credentials file : resources/credentials.properties");					
			System.exit(-1);
		}
		
		capi = new CryptsyApi(cred.getProperty("key"),cred.getProperty("secret"));
	}

	
	public static void main(String[] args) {
		
		java.util.HashMap<String, String> depth=new java.util.HashMap<>();
		depth.put("depth","5");

//		java.util.HashMap<String, Long> stale=new java.util.HashMap<>();
		
//		FileWriter fw2=null;
		try {
//			fw2=new FileWriter("../log/conversions.csv",true);				
//			fw2.write("TIMESTAMP;BTC-GHS-NMC-BTC;BTC-NMC-GHD-BTC" + System.getProperty("line.separator"));


				for (int j=0;j<PAIRS.length;j++){
//					OrderBook ob=null;
					try {
//						JsonNode jnob2=CryptsyApi.queryPublic("marketdatav2",null);
//						System.exit(-1);
						
						JsonNode jnob=CryptsyApi.queryPublic("singleorderdata",PAIRS[j]);
//						ob=new OrderBook(jnob);
						System.out.println(jnob);
					} catch (NullPointerException npe){
						// ERROR already printed before	during query
					}
				}
				

				System.out.println(capi.queryPrivate("getinfo", null));
				
		} catch (Exception e){
			e.printStackTrace(System.err);
		} 
	}
}
