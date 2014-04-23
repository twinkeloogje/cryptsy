package be.twinkel.cryptsy.treemodel;

import com.fasterxml.jackson.databind.JsonNode;

public class TradeNode {
	private double price;
	private double volume;
	
	public TradeNode(JsonNode jn/*,String pricetag*/){
		price=jn.get("price" /*pricetag*/).asDouble();
		volume=jn.get("quantity").asDouble();
	}

	public double getPrice() {
		return price;
	}

	public double getVolume() {
		return volume;
	}	
}
