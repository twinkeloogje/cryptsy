package be.twinkel.cryptsy.helper;

import java.util.Date;

import be.twinkel.cryptsy.core.CryptsyApi;
import be.twinkel.cryptsy.treemodel.TradeNode;

public class PairDataHelper {
	private static final double SELLFEE=0.003d,BUYFEE=0.002d;
	
	public static final int MAXAGE=2000;
	
	public static final int FAILED=-2;
	public static final int USED=-1;
	public static final int STALE=0;
	public static final int FALLING=1;
	public static final int SPREADING=2;
	public static final int NARROWING=3;
	public static final int RISING=4;
	
	private long lastrefresh=0;
	private int lastloadresult=0 /*STALE*/;
	private TradeNode[] tn=new TradeNode[2];
	private String pair=null;
	//private CryptsyApi capi=null;
	
	private double maxin=1000.0d;
	private double maxout=1000.0d;
	private double minvol=0.001d;
	//private double fee=0.003d;
			
	private boolean used=true;
	
	public int loadData(){
		long now=(new Date()).getTime();
		if ((now-lastrefresh<MAXAGE)&&(!used)&&(lastloadresult>0)){
			return lastloadresult;
		}
		
		TradeNode[][] booktop=CryptsyApi.queryDepth(pair, 1);
		//TradeNode[][] booktop=capi.queryDepth(pair, 1);
		if (booktop==null){
			lastloadresult=FAILED;
			return FAILED;
		}
		
		if (tn[0]==null){
			lastloadresult=SPREADING;			
		} else {

		// check vol change after 	USAGE
			
		if (used){
			
			if ( (booktop[CryptsyApi.ASK][0].getPrice()==tn[CryptsyApi.ASK].getPrice()) && 
				 (booktop[CryptsyApi.BID][0].getPrice()==tn[CryptsyApi.BID].getPrice()) && 
				 (booktop[CryptsyApi.ASK][0].getVolume()==tn[CryptsyApi.ASK].getVolume()) && 
				 (booktop[CryptsyApi.BID][0].getVolume()==tn[CryptsyApi.BID].getVolume())) {
				return STALE;
			}	
		}
		
		if (booktop[CryptsyApi.ASK][0].getPrice()<=booktop[CryptsyApi.BID][0].getPrice()){
			System.err.println("WARNING : UNPROCESSED ORDER detected for market "+pair+" !!  ASK="+booktop[CryptsyApi.ASK][0].getPrice()+" BID="+booktop[CryptsyApi.BID][0].getPrice());
			return STALE;
		}
		
		if (booktop[CryptsyApi.ASK][0].getPrice()>tn[CryptsyApi.ASK].getPrice()){
			if (booktop[CryptsyApi.BID][0].getPrice()<tn[CryptsyApi.BID].getPrice()){
				lastloadresult=SPREADING;
			}
			if (booktop[CryptsyApi.BID][0].getPrice()==tn[CryptsyApi.BID].getPrice()){
				if (lastloadresult!=RISING) lastloadresult=SPREADING; 
			}
			if (booktop[CryptsyApi.BID][0].getPrice()>tn[CryptsyApi.BID].getPrice()){
				lastloadresult=RISING;
			}			
		}
		if (booktop[CryptsyApi.ASK][0].getPrice()==tn[CryptsyApi.ASK].getPrice()){
			if (booktop[CryptsyApi.BID][0].getPrice()<tn[CryptsyApi.BID].getPrice()){
				if (lastloadresult!=FALLING) lastloadresult=SPREADING;
			}
			if (booktop[CryptsyApi.BID][0].getPrice()==tn[CryptsyApi.BID].getPrice()){
				if (lastloadresult<1) lastloadresult=SPREADING;
			}
			if (booktop[CryptsyApi.BID][0].getPrice()>tn[CryptsyApi.BID].getPrice()){
				if (lastloadresult!=RISING) lastloadresult=NARROWING;
			}			
			
		}
		if (booktop[CryptsyApi.ASK][0].getPrice()<tn[CryptsyApi.ASK].getPrice()){
			if (booktop[CryptsyApi.BID][0].getPrice()<tn[CryptsyApi.BID].getPrice()){
				lastloadresult=FALLING;
			}
			if (booktop[CryptsyApi.BID][0].getPrice()==tn[CryptsyApi.BID].getPrice()){
				if (lastloadresult!=FALLING) lastloadresult=NARROWING; 				
			}
			if (booktop[CryptsyApi.BID][0].getPrice()>tn[CryptsyApi.BID].getPrice()){
				lastloadresult=NARROWING;
			}			
			
		}
		
		}
		
		lastrefresh=now;
		tn[CryptsyApi.BID]=booktop[CryptsyApi.BID][0];
		tn[CryptsyApi.ASK]=booktop[CryptsyApi.ASK][0];
		setUsed(false);
		return lastloadresult;
	}
	
	public PairDataHelper(String inpair/*,double infee,CryptsyApi incapi*/){
		pair=inpair;
		//capi=incapi;
		//fee=infee;
		
		if (pair.equals("3")){ maxin=4.0d ; maxout=0.1d ;}
		if (pair.equals("132")){ maxout=0.1d ;}
		if (pair.equals("135")){ maxout=4.0d ;}
		
	}

	public boolean isUsed() {
		return used;
	}

	public void setUsed(boolean used) {
		this.used = used;
	}

	public TradeNode getTn(int i) {
		return tn[i];
	}

	public double getMaxin() {
		return maxin;
	}

	public double getMaxout() {
		return maxout;
	}
	public double getMinvol() {
		return minvol;
	}

	public double[] getFees() {
		// SELL , BUY
		return new double[]{1.0d-SELLFEE,1.0d+BUYFEE};
	}
	
}
