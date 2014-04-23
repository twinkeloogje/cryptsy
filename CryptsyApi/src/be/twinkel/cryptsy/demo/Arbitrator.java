package be.twinkel.cryptsy.demo;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

//import com.fasterxml.jackson.databind.JsonNode;





import com.fasterxml.jackson.databind.JsonNode;

import be.twinkel.cryptsy.helper.PairDataHelper;
import be.twinkel.cryptsy.core.CryptsyApi;
//import be.twinkel.kraken.treemodel.OrderNode;
//import be.twinkel.kraken.exception.UnsupportedMethodException;
//import be.twinkel.kraken.treemodel.TickerNode;
import be.twinkel.cryptsy.treemodel.TradeNode;

public class Arbitrator {
	// list pairs in order of increasing volatility/chance, so order decision is as close as possible to read operation
	private static final String[] PAIRS= { "3","132","135","66","121","160","161",/*"181","186",*/"179","191","70","123","8","17",
		"26","96","40","52","47","55","33","171","44","124","30","36","59","60","25","35","45","100","134","108","159","162",
		"28","125","31","101","71","126","9","37","51","128","153","98","14","21","63","106","11","22","85","127" };

	
	//	private static final double /*PFEE ; //,*/SELLFEE=0.003d,BUYFEE=0.002d;
//	private static final double RFEE,SELLRFEE,BUYRFEE;

	
	private static CryptsyApi crapi;
	
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
		
		crapi = new CryptsyApi(cred.getProperty("key"),cred.getProperty("secret"));

/*		
		double pfee=0.0d;
		try{
			pfee=Double.valueOf(cred.getProperty("fee")).doubleValue();
		} catch (NumberFormatException nfe){
			System.err.println("ERR-0003 : Trader init couldn't parse credentials file : invalid number for \"fee\"");					
			System.exit(-1);
		}
		PFEE=pfee;
*/		
/*		SELLFEE=1.0d-PFEE;
		BUYFEE=1.0d+PFEE;
		RFEE=0.0005d;
		SELLRFEE=1.0d-RFEE;
		BUYRFEE=1.0d+RFEE; */
	}

	
//	private static final String PAIRSCSV;
	
//	private static final HashMap<String,TradeNode[]> orderBookTops= new HashMap<String,TradeNode[]>();
	private static final HashMap<String,PairDataHelper> orderBooks= new HashMap<String,PairDataHelper>();

	/*	
	static{
		String concat=PAIRS[0];
		for (int i=1;i<PAIRS.length;i++){
			concat+=","+PAIRS[i];
		}
		PAIRSCSV=concat;
	}
*/
	
	private static double getInputForOutputFromSell(double out, double price,double fee){
		// in:base out:quote
		
		// out=in*price*(1-PFEE);
		// out/(1-PFEE)/price=in;		
//		return out/price/SELLFEE;
		return out/price/fee;
	}
	private static double getOutputForInputFromSell(double in, double price,double fee){
		// in:base out:quote
//		return in*price*SELLFEE;		
		return in*price*fee;
	}

	private static double getInputForOutputFromBuy(double out, double price,double fee){
		// in:quote out:base
//		return out*price*BUYFEE;
		return out*price*fee;
	}
	private static double getOutputForInputFromBuy(double in, double price,double fee){
		// in:quote out:base
		
		// in == out*price*(1.0d+PFEE)
		// in/(1.0d+PFEE)/price=out
		
//		return in/price/BUYFEE;
		return in/price/fee;
	}

	private static double[] rebaseVolumes(double[] propvols,TradeNode tn, boolean sell,double[] fees){
		// propvols = { baseprop , quoteprop }
		final int BASE=0;
		final int QUOTE=1;
				
		double[][] sims=new double[3][2];
		
		sims[0][BASE]=propvols[BASE]; // base
		sims[1][QUOTE]=propvols[QUOTE]; // quote

		sims[2][BASE]=tn.getVolume(); // base
		
		if (sell) {
			sims[0][QUOTE]=getOutputForInputFromSell(propvols[BASE],tn.getPrice(),fees[CryptsyApi.SELL]);		
			sims[1][BASE]=getInputForOutputFromSell(propvols[QUOTE],tn.getPrice(),fees[CryptsyApi.SELL]);
						
			sims[2][QUOTE]=getOutputForInputFromSell(tn.getVolume(),tn.getPrice(),fees[CryptsyApi.SELL]);
		} else {
			sims[0][QUOTE]=getInputForOutputFromBuy(propvols[BASE],tn.getPrice(),fees[CryptsyApi.BUY]);		
			sims[1][BASE]=getOutputForInputFromBuy(propvols[QUOTE],tn.getPrice(),fees[CryptsyApi.BUY]);
						
			sims[2][QUOTE]=getInputForOutputFromBuy(tn.getVolume(),tn.getPrice(),fees[CryptsyApi.BUY]);
		}
		
		double basevol=sims[0][BASE];
		double quotevol=sims[0][QUOTE];
		
		if (sell){
			if (sims[1][BASE] < basevol) {
				basevol=sims[1][BASE];
				quotevol=sims[1][QUOTE];
			}
			if (sims[2][BASE] < basevol) {
				basevol=sims[2][BASE];
				quotevol=sims[2][QUOTE];
			}
		} else {
			if (sims[1][QUOTE] < quotevol) {
				basevol=sims[1][BASE];
				quotevol=sims[1][QUOTE];
			}
			if (sims[2][QUOTE] < quotevol) {
				basevol=sims[2][BASE];
				quotevol=sims[2][QUOTE];
			}			
		}
		
		return new double[]{basevol,quotevol};
	}
		
	private static double[] calculateVolumes(TradeNode[] tna,String order,String[] pairs){

		final int BASE=0;
		final int QUOTE=1;

		final int SELL=1;
		final int BUY=0;

		///  assume 3
		double[][] sims=new double[3][2];
		int op0,op1,op2;

		PairDataHelper curpdh=orderBooks.get(pairs[0]);
		
		if (order.charAt(0)=='S'){		
			sims[0]=rebaseVolumes(new double[]{curpdh.getMaxin(),curpdh.getMaxout()}, tna[0], true, curpdh.getFees());
			op0=SELL;
		} else {
			sims[0]=rebaseVolumes(new double[]{curpdh.getMaxin(),curpdh.getMaxout()}, tna[0], false, curpdh.getFees());
			op0=BUY;
		}
		
		curpdh=orderBooks.get(pairs[1]);
		double base=curpdh.getMaxin();
		double quote=curpdh.getMaxout();
		if (order.charAt(1)=='S'){
			
			if (op0==SELL){
				base=sims[0][QUOTE];
			}else{
				base=sims[0][BASE];
			}
			
			sims[1]=rebaseVolumes(new double[]{base,quote}, tna[1], true, curpdh.getFees());
			op1=SELL;
		} else {
			
			if (op0==SELL){
				quote=sims[0][QUOTE];
			}else{
				quote=sims[0][BASE];
			}
			
			sims[1]=rebaseVolumes(new double[]{base,quote}, tna[1], false,curpdh.getFees());
			op1=BUY;
		}

		curpdh=orderBooks.get(pairs[2]);
		base=curpdh.getMaxin();
		quote=curpdh.getMaxout();
		if (order.charAt(2)=='S'){
			
			if (op1==SELL){
				base=sims[1][QUOTE];
			}else{
				base=sims[1][BASE];
			}
						
			sims[2]=rebaseVolumes(new double[]{base,quote}, tna[2], true,curpdh.getFees());
			op2=SELL;
		} else {
			
			if (op1==SELL){
				quote=sims[1][QUOTE];
			}else{
				quote=sims[1][BASE];
			}
						
			sims[2]=rebaseVolumes(new double[]{base,quote}, tna[2], false,curpdh.getFees());
			op2=BUY;
		}
		
		System.out.println();
		System.out.println(""+sims[0][1-op0]+' '+sims[0][op0]+' '+sims[1][1-op1]+' '+sims[1][op1]+' '+sims[2][1-op2]+' '+sims[2][op2]);
		
		// now carry back
		
		// if (op1==SELL(==QUOTE) && op2==SELL(==QUOTE))  compare : [1][QUOTE] to [2][BASE]
		// if (op1==SELL(==QUOTE) && op2==BUY(==BASE))    compare : [1][QUOTE] to [2][QUOTE]
		// if (op1==BUY(==BASE)   && op2==SELL(==QUOTE))  compare : [1][BASE]  to [2][BASE]
		// if (op1==BUY(==BASE)   && op2==BUY(==BASE))    compare : [1][BASE]  to [2][QUOTE]
		
		if (sims[1][op1]>sims[2][1-op2]){
			sims[1][op1]=sims[2][1-op2];
			sims[1]=rebaseVolumes(sims[1], tna[1],(op1==SELL?true:false),orderBooks.get(pairs[1]).getFees());
		}
		
		if (sims[0][op0]>sims[1][1-op1]){
			sims[0][op0]=sims[1][1-op1];
			sims[0]=rebaseVolumes(sims[0], tna[0],(op0==SELL?true:false),orderBooks.get(pairs[0]).getFees());
		}

		System.out.println(""+sims[0][1-op0]+' '+sims[0][op0]+' '+sims[1][1-op1]+' '+sims[1][op1]+' '+sims[2][1-op2]+' '+sims[2][op2]);
		System.out.println("return "+sims[0][0]+' '+sims[1][0]+' '+sims[2][0]);
		
		return new double[]{sims[0][BASE],sims[1][BASE],sims[2][BASE]};
		
	}

	private static double evaluateCircle(String[] pairs,String order){
		
		// load/update requested pairs
		
		for (int j=0;j<pairs.length;j++){
			int result=orderBooks.get(pairs[j]).loadData();
			if (result<=0)
				return 0.0d;
		}
				
		// assert pairs.length==order.size

		double result=1.0d;
//		double[][] fees=new double[pairs.length][2];
		
//		String rotation="";
		
		for (int i=0; i< pairs.length;i++){
/*			
			if (pairs[i].indexOf("XXRP")>-1){
				fees[i][Kapi.BUY]=BUYRFEE;
				fees[i][Kapi.SELL]=SELLRFEE;
			} else {
				fees[i][Kapi.BUY]=BUYFEE;
				fees[i][Kapi.SELL]=SELLFEE;
			}
*/			
			switch (order.charAt(i)){
			case 'S':
				result*= (orderBooks.get(pairs[i])).getTn(CryptsyApi.BID).getPrice();
				// tna[i].getBid_price();
				result*=(orderBooks.get(pairs[i])).getFees()[CryptsyApi.SELL];
//				rotation+=pairs[i].substring(1,4);
				break;
			case 'B':
				result/= (orderBooks.get(pairs[i])).getTn(CryptsyApi.ASK).getPrice();
				// tna[i].getAsk_price();
				result/=(orderBooks.get(pairs[i])).getFees()[CryptsyApi.BUY];
//				rotation+=pairs[i].substring(5,8);
				break;
			default:
				System.exit(-1);			
			}
		}
				
		if ((result>1.001d)&&(result<1.05d)){ // need to make some profit at least -- more than 5% = too good to be true !!!

			TradeNode[] trna2=new TradeNode[pairs.length];
			for (int i=0; i< pairs.length;i++){

				//TradeNode[][] trna=Kapi.queryDepth(tna[i].getPair(), 1);
				
				switch (order.charAt(i)){
				case 'S':
					trna2[i]=(orderBooks.get(pairs[i])).getTn(CryptsyApi.BID);
					break;
				case 'B':
					trna2[i]=(orderBooks.get(pairs[i])).getTn(CryptsyApi.ASK);
					break;
				default:
					System.exit(-1);			
				}
			}
			double[] vols=calculateVolumes(trna2, order, pairs);
			
			boolean minavail=true;
			for (int i=0; i< pairs.length;i++){
				if (vols[i]<orderBooks.get(pairs[i]).getMinvol()) minavail=false; 
				if ((orderBooks.get(pairs[i]).getTn(CryptsyApi.BID).getPrice())<0.00002d) minavail=false; 
			}
			
			if (minavail){
				
				for (int i=0; i< pairs.length;i++){
					JsonNode txid=null;
				
					double tradevolume=vols[i];
										
					switch (order.charAt(i)){
					case 'S':
						txid=crapi.sell(pairs[i],(orderBooks.get(pairs[i])).getTn(CryptsyApi.BID).getPrice(),tradevolume);
						break;
					case 'B':
						txid=crapi.buy(pairs[i],(orderBooks.get(pairs[i])).getTn(CryptsyApi.ASK).getPrice(),tradevolume);
						break;
					default:
						System.exit(-1);			
					}
					System.out.println(txid);
					orderBooks.get(pairs[i]).setUsed(true);
				}
				// Stop after first cycle
				// System.exit(-1);											
			}
		}
		
		if (result>1.001d){
			try {			
				FileWriter fw=new FileWriter("../log/RotationData.csv",true);
				fw.write(result+";"+order);

				
//				long[] maxvol=new long;
				
				
				for (int i=0; i< pairs.length;i++){
										
					fw.write(";"+pairs[i]);
					// TradeNode[][] trna=Kapi.queryDepth(tna[i].getPair(), 1);
					fw.write(";"+(orderBooks.get(pairs[i])).getTn(CryptsyApi.ASK).getPrice());
					fw.write(";"+(orderBooks.get(pairs[i])).getTn(CryptsyApi.ASK).getVolume());
//					fw.write(";"+(orderBookTops.get(pairs[i]))[BtceApi.ASK].getTimestamp());
					fw.write(";"+(orderBooks.get(pairs[i])).getTn(CryptsyApi.BID).getPrice());
					fw.write(";"+(orderBooks.get(pairs[i])).getTn(CryptsyApi.BID).getVolume());
//					fw.write(";"+(orderBookTops.get(pairs[i]))[BtceApi.BID].getTimestamp());
				}
				long ts=(new Date()).getTime();
				fw.write(";"+ts+System.getProperty("line.separator"));
				fw.close();
			} catch (Exception e){
				e.printStackTrace(System.err);
			}

		}

		return result;		
	} 
		
	public static void main(String[] args) {
		FileWriter fw2=null;
		try {

//			FileWriter fw=new FileWriter("E:\\Media\\Kraken\\XBTrates.csv",true);
			fw2=new FileWriter("../log/Conversions.csv",true);			
//			HashMap<String,String> m=new HashMap<String,String>();
//			m.put("pair", "XXBTXLTC,XXBTXNMC,XXBTZEUR,XXBTZUSD,XLTCZEUR,XLTCZUSD,XNMCZEUR,XNMCZUSD");
	

			fw2.write("TIMESTAMP"
					+ ";XBT-EUR-LTC-XBT;XBT-LTC-EUR-XBT"
					+ ";XBT-EUR-NMC-XBT;XBT-NMC-EUR-XBT"
					+ ";XBT-EUR-XDG-XBT;XBT-XDG-EUR-XBT"
					+  System.getProperty("line.separator"));

			
			for (int j=0;j<PAIRS.length;j++){
				PairDataHelper cpaird=new PairDataHelper(PAIRS[j]/*,PFEE,crapi*/);
				cpaird.loadData();
				orderBooks.put(PAIRS[j], cpaird);
			}
			
			
			int i=0;
			long lastround=0l;
			
			while (true) {
				Thread.sleep(1000l);
				//for (int i=0;i<3600;i++){

				long now=(new Date()).getTime();
				long delta=now-lastround;
				//System.out.print(delta);
				
				if (delta<2000l){
					Thread.sleep(1000l);
				}
				lastround=now;
				
//				Map<String,TickerNode> tnm=Kapi.queryTicker(PAIRSCSV);

/*				
				boolean all=true;
				for (int j=0;j<PAIRS.length;j++){
					TradeNode[][] booktop=Kapi.queryDepth(PAIRS[j], 1);
					if (booktop==null)
						all=false;
					else
						orderBookTops.put(PAIRS[j],new TradeNode[]{booktop[Kapi.ASK][0],booktop[Kapi.BID][0]});
				}
				
				
				if (!all) {
					if (((i+1)/20)*20==(i+1)){
						System.out.println("-"+((i+1)/20));
					} else {
						System.out.print("-");
					}
					
				} else {
*/
					if (((i+1)/30)*30==(i+1)){
						System.out.println("+"+((i+1)/30));
					} else {
						System.out.print("+");
					}	
				
				
//				Iterator<String> it=tnm.keySet().iterator();
				
					long ts=(new Date()).getTime();
			
//				fw.write(""+ts);
					fw2.write(""+ts);

// LTC BTC 3
					
					
// ANC BTC 66
// ANC LTC 121
					fw2.write(";"+evaluateCircle(new String[]{"3","66","121"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","121","66"}, "BBS"));

// AUR BTC 160
// AUR LTC 161
					fw2.write(";"+evaluateCircle(new String[]{"3","160","161"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","161","160"}, "BBS"));

/*
// BAT BTC 181
// BAT LTC 186
					fw2.write(";"+evaluateCircle(new String[]{"3","181","186"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","181","186"}, "BSB"));
*/
					
// BC BTC 179
// BC LTC 191
					fw2.write(";"+evaluateCircle(new String[]{"3","179","191"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","191","179"}, "BBS"));

// CGB BTC 70
// CGB LTC 123
					fw2.write(";"+evaluateCircle(new String[]{"3","70","123"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","123","70"}, "BBS"));

/*
// CNC BTC 8
// CNC LTC 17
					fw2.write(";"+evaluateCircle(new String[]{"3","8","17"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","17","8"}, "BBS"));
*/
					
// DGC BTC 26
// DGC LTC 96
					fw2.write(";"+evaluateCircle(new String[]{"3","26","96"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","96","26"}, "BBS"));

/*
// DOGE BTC 132
// DOGE LTC 135
					// DOGE
					// XBT-LTC-XDG-XBT
					fw2.write(";"+evaluateCircle(new String[]{"3","132","135"}, "SBS"));
					// XBT-XDG-LTC-XBT
					fw2.write(";"+evaluateCircle(new String[]{"3","135","132"}, "BBS"));
					
// DVC BTC 40
// DVC LTC 52
					fw2.write(";"+evaluateCircle(new String[]{"3","40","52"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","52","40"}, "BBS"));

// EZC BTC 47
// EZC LTC 55
					fw2.write(";"+evaluateCircle(new String[]{"3","47","55"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","55","47"}, "BBS"));
*/

// FRK BTC 33
// FRK LTC 171
					fw2.write(";"+evaluateCircle(new String[]{"3","33","171"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","171","33"}, "BBS"));

/*
// FST BTC 44
// FST LTC 124
					fw2.write(";"+evaluateCircle(new String[]{"3","44","124"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","124","44"}, "BBS"));
*/
					
// GLD BTC 30
// GLD LTC 36
					fw2.write(";"+evaluateCircle(new String[]{"3","30","36"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","36","30"}, "BBS"));

/*					
// IFC BTC 59
// IFC LTC 60
					fw2.write(";"+evaluateCircle(new String[]{"3","59","60"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","60","59"}, "BBS"));
										
// JKC BTC 25
// JKC LTC 35
					fw2.write(";"+evaluateCircle(new String[]{"3","25","35"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","35","25"}, "BBS"));
*/

// MEC BTC 45
// MEC LTC 100
					fw2.write(";"+evaluateCircle(new String[]{"3","45","100"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","100","45"}, "BBS"));

/*					
// NET BTC 134
// NET LTC 108
					fw2.write(";"+evaluateCircle(new String[]{"3","134","108"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","108","134"}, "BBS"));
*/
					
// NXT BTC 159
// NXT LTC 162
					fw2.write(";"+evaluateCircle(new String[]{"3","159","162"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","162","159"}, "BBS"));

// PPC BTC 28
// PPC LTC 125
					fw2.write(";"+evaluateCircle(new String[]{"3","28","125"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","125","28"}, "BBS"));

/*					
// PXC BTC 31
// PXC LTC 101
					fw2.write(";"+evaluateCircle(new String[]{"3","31","101"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","101","31"}, "BBS"));
*/
					
// QRK BTC 71
// QRK LTC 126
					fw2.write(";"+evaluateCircle(new String[]{"3","71","126"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","126","71"}, "BBS"));

/*
// RYC BTC 9
// RYC LTC 37
					fw2.write(";"+evaluateCircle(new String[]{"3","9","37"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","37","9"}, "BBS"));

// SBC BTC 51
// SBC LTC 128
					fw2.write(";"+evaluateCircle(new String[]{"3","51","128"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","128","51"}, "BBS"));

// SXC BTC 153
// SXC LTC 98
					fw2.write(";"+evaluateCircle(new String[]{"3","153","98"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","98","153"}, "BBS"));
*/

// WDC BTC 14
// WDC LTC 21
					fw2.write(";"+evaluateCircle(new String[]{"3","14","21"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","21","14"}, "BBS"));

// XPM BTC 63
// XPM LTC 106
					fw2.write(";"+evaluateCircle(new String[]{"3","63","106"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","106","63"}, "BBS"));

/*					
// YAC BTC 11
// YAC LTC 22
					fw2.write(";"+evaluateCircle(new String[]{"3","11","22"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","22","11"}, "BBS"));
*/
					
// ZET BTC 85
// ZET LTC 127
					fw2.write(";"+evaluateCircle(new String[]{"3","85","127"}, "SBS"));
					fw2.write(";"+evaluateCircle(new String[]{"3","127","85"}, "BBS"));

					
//				fw.write(System.getProperty("line.separator"));
//				fw.flush();
					fw2.write(System.getProperty("line.separator"));
					fw2.flush();
				/* } */
				i++;
			}	
				
//			fw.close();
//			fw2.close();
			
		} catch (Exception e){
			e.printStackTrace(System.err);
		} finally{
			try{
				fw2.close();
			} catch (Exception e){
				
			}
		}
		
		
	}

}
