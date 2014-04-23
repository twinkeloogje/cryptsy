package be.twinkel.cryptsy.core;

//import java.text.DecimalFormat;
//import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
//import java.util.Locale;
// import java.util.Locale;
import java.util.Map;
import java.io.*;
import java.net.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import be.twinkel.cryptsy.treemodel.TradeNode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*markets
 * 
 * 
 * 

ANC BTC 66
ANC LTC 121
AUR BTC 160
AUR LTC 161
BAT BTC 181
BAT LTC 186
BC BTC 179
BC LTC 191
CGB BTC 70
CGB LTC 123
CNC BTC 8
CNC LTC 17
DGC BTC 26
DGC LTC 96
DOGE BTC 132
DOGE LTC 135
DVC BTC 40
DVC LTC 52
EZC BTC 47
EZC LTC 55
FRK BTC 33
FRK LTC 171
FST BTC 44
FST LTC 124
GLD BTC 30
GLD LTC 36
IFC BTC 59
IFC LTC 60
JKC BTC 25
JKC LTC 35

LTC BTC 3

MEC BTC 45
MEC LTC 100
NET BTC 134
NET LTC 108
NXT BTC 159
NXT LTC 162
PPC BTC 28
PPC LTC 125
PXC BTC 31
PXC LTC 101
QRK BTC 71
QRK LTC 126
RYC BTC 9
RYC LTC 37
SBC BTC 51
SBC LTC 128
SXC BTC 153
SXC LTC 98
WDC BTC 14
WDC LTC 21
XPM BTC 63
XPM LTC 106
YAC BTC 11
YAC LTC 22
ZET BTC 85
ZET LTC 127

 * 
 * */

public class CryptsyApi {
	public static final String BALANCE = "getInfo";
	public static final String PRECISION = "0.########";

	private String key = "";
	private String secret = "";
	public static final String privuri = "https://api.cryptsy.com/api";
	public static final String publuri = "http://pubapi.cryptsy.com/api.php?method=";
	private static final String charset = "UTF-8";
	private static long lastnonce = 0;

	public static final int ASK = 0;
	public static final int BID = 1;	

	public static final int SELL=0;
	public static final int BUY=1;
		
	public CryptsyApi() {
		this("", "");
	}

	private static synchronized String getNonce(){
		long now=(new java.util.Date()).getTime();
		
		if (now>lastnonce)
			lastnonce=now;
		else
			lastnonce+=1;
		
		return ""+lastnonce;
	}

	public CryptsyApi(String inkey, String insecret) {
		if (inkey == null)
			key = "";
		else
			key = inkey;
		if (insecret == null)
			secret = "";
		else
			secret = insecret;
	}

	private String SignMessage(String postdata) {
		try {
			Mac sha512_HMAC = Mac.getInstance("HmacSHA512");
			SecretKeySpec secret_key = new SecretKeySpec(
					secret.getBytes(charset), "HmacSHA512");
			sha512_HMAC.init(secret_key);

			return DatatypeConverter.printHexBinary(
					sha512_HMAC.doFinal(postdata.getBytes(charset))).toLowerCase();
		} catch (Exception e) {
			System.err.println("Error");
			e.printStackTrace(System.err);
			return null;
		}
	}

	private JsonNode UrlHelperSecure(/*String method, */String encodedquery)
			throws IOException {
		// System.out.println(uri+urlpath);
		ObjectMapper mapper = new ObjectMapper();

		URL url = new URL(privuri /* + method */);
		URLConnection connection = url.openConnection();

		connection.setDoOutput(true);
		connection.setRequestProperty("User-Agent",
				"twinkel.be Cryptsy Java API Agent");
		connection.setRequestProperty("Accept-Charset", charset);
		connection.setRequestProperty("Content-Type",
				"application/x-www-form-urlencoded;charset=" + charset);

		connection.setRequestProperty("Key", key);
		connection.setRequestProperty("Sign", SignMessage(encodedquery));

		// System.out.println("Key="+key);
		// System.out.println("Sign="+SignMessage(encodedquery));

		if (encodedquery != null) {
			OutputStream output = connection.getOutputStream();
			try {
				output.write(encodedquery.getBytes(charset));
			} catch (Exception e) {
				e.printStackTrace(System.err);
			} finally {
				try {
					output.close();
				} catch (IOException logOrIgnore) {
					// TODO : fatal error
				}
			}
		}

		String response = convertStreamToString(connection.getInputStream());

		try {
			JsonNode rootnode = mapper.readValue(response, JsonNode.class);
			return rootnode;
		} catch (Exception e) {
			System.err.println("ERROR: " + response);
			return null;
		}
	}

	static String convertStreamToString(java.io.InputStream is) {
		java.util.Scanner s = null;
		try {
			s = new java.util.Scanner(is).useDelimiter("\\A");

			return s.hasNext() ? s.next() : "";
		} finally {

			s.close();
		}
	}

	private static JsonNode UrlHelper(String method, String market)
			throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		// System.out.println(publuri+urlpath);
		
		String fullurl=publuri + method;
		if ((market!=null)&&(market.length()>0)){
			fullurl+="&marketid="+market;
		}
		// System.out.println(fullurl);
		

		URL url = new URL(fullurl);
		URLConnection connection = url.openConnection();

		connection.setDoOutput(true);
		connection.setRequestProperty("User-Agent",
				"twinkel.be Cryptsy Java API Agent");
		connection.setRequestProperty("Accept-Charset", charset);
		connection.setRequestProperty("Content-Type",
				"application/x-www-form-urlencoded;charset=" + charset);
/*
		if (encodedquery != null) {

			OutputStream output = connection.getOutputStream();
			try {
				output.write(encodedquery.getBytes(charset));
			} catch (Exception e) {
				e.printStackTrace(System.err);
				System.exit(-1);
			} finally {
				try {
					output.close();
				} catch (IOException logOrIgnore) {

					System.exit(-1);

				}
			}

		}
*/		
		JsonNode rootnode = mapper.readValue(connection.getInputStream(),JsonNode.class);

		// System.out.println(rootnode.toString());
		return rootnode;
	}

	public static JsonNode queryPublic(String method, String pair/*,
			Map<String, String> params*/) /* throws UnsupportedMethodException */{

		//String urlpath = pair + "/" + method;

		try {
			JsonNode response = UrlHelper(method, pair);
			// if (response==null) return null;
			return response;
			// if (!checkErrors(response[0])){
			// return response[1];
			// }
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
		return null;
	}

	public JsonNode queryPrivate(String method, Map<String, String> params) /*
																			 * throws
																			 * UnsupportedMethodException
																			 */{
		if (params == null) { // If the user provided no arguments, just create
								// an empty argument array.
			params = new HashMap<String, String>();
		}
		/*
		 * if (!( (BALANCE.equals(method)) || (TRADEBALANCE.equals(method)) ||
		 * (OPENORDERS.equals(method)) || (CLOSEDORDERS.equals(method)) ||
		 * (ADDORDER.equals(method))
		 *//*
			 * || ("".equals(method)) || ("".equals(method))
			 *//*
				 * )) {
				 * 
				 * throw new UnsupportedMethodException(); }
				 */
		String nonce = getNonce();
		params.put("nonce", nonce);
		params.put("method", method);

		try {
			JsonNode response = UrlHelperSecure(formatQueryParams(params));
			return response;
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
		return null;
	}

	private static String formatQueryParams(Map<String, String> params) {

		if ((params == null) || (params.size() == 0))
			return null;

		String query = "";

	
		try {

			Iterator<String> it = params.keySet().iterator();

			if (it.hasNext()) {

				String pname = it.next();
				query = pname + "=" /* + params.get(pname); */
						+ URLEncoder.encode(params.get(pname), charset);

			} else {
				// TODO : fatal error
			}

			while (it.hasNext()) {
				String pname = it.next();
				query = query + "&" + pname + "=" /*+params.get(pname);*/
						+ URLEncoder.encode(params.get(pname), charset);
			}

		} catch (UnsupportedEncodingException uee) {
			uee.printStackTrace(System.err);
			return null;
		}
		
		// DEBUG System.out.println(query);
		return query;
	}

	
	public JsonNode buy(String pair, double price, double vol) {

		NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
	    DecimalFormat myFormatter = (DecimalFormat)nf;
	    myFormatter.applyPattern(PRECISION);
		
		java.util.HashMap<String, String> buyorder = new java.util.HashMap<String, String>();
		buyorder.put("marketid", pair);
		buyorder.put("ordertype", "buy");
		buyorder.put("price", myFormatter.format(price));
		buyorder.put("quantity", myFormatter.format(vol));

		System.out.println("BUY : "+pair+" "+myFormatter.format(vol)+" "+myFormatter.format(price));

		JsonNode cancel = queryPrivate("createorder", buyorder);
		// System.out.println(cancel);
		return cancel;
	}

	public JsonNode sell(String pair, double price, double vol) {

		NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
	    DecimalFormat myFormatter = (DecimalFormat)nf;
	    myFormatter.applyPattern(PRECISION);

		java.util.HashMap<String, String> sellorder = new java.util.HashMap<String, String>();
		sellorder.put("marketid", pair);
		sellorder.put("ordertype", "sell");
		sellorder.put("price", myFormatter.format(price));
		sellorder.put("quantity", myFormatter.format(vol));
		
		System.out.println("SELL: "+pair+" "+myFormatter.format(vol)+" "+myFormatter.format(price));

		JsonNode cancel = queryPrivate("createorder", sellorder);
		// System.out.println(cancel);
		return cancel;
	}
	
	public static TradeNode[][] queryDepth(String pair, int depth) {
		TradeNode[][] retv = new TradeNode[2][depth];

		JsonNode jnt = CryptsyApi.queryPublic("singleorderdata", pair);
		if (jnt == null)
			return null;

		try {
			for (int j = 0; j < depth; j++) {
				// System.err.println(jn);
				retv[ASK][j] = new TradeNode(jnt.findValue("sellorders").get(j));
			}
			for (int j = 0; j < depth; j++) {
				// System.err.println(jn);
				retv[BID][j] = new TradeNode(jnt.findValue("buyorders").get(j));
			}
		} catch (NullPointerException npe) {
			System.err.println("#0002 : NPError while retrieving asks/bids: "
					+ jnt);
			return null;
		}

		return retv;
	}

/*	
	public TradeNode[][] queryDepth(String pair, int depth) {
		TradeNode[][] retv = new TradeNode[2][depth];

		java.util.HashMap<String, String> params = new java.util.HashMap<String, String>();
		params.put("marketid", pair);
		
		JsonNode jnt = queryPrivate("marketorders", params);
		if (jnt == null)
			return null;

		try {
			for (int j = 0; j < depth; j++) {
				// System.err.println(jn);
				retv[ASK][j] = new TradeNode(jnt.findValue("sellorders").get(j),"sellprice");
			}
			for (int j = 0; j < depth; j++) {
				// System.err.println(jn);
				retv[BID][j] = new TradeNode(jnt.findValue("buyorders").get(j),"buyprice");
			}
		} catch (NullPointerException npe) {
			System.err.println("#0002 : NPError while retrieving asks/bids: "
					+ jnt);
			return null;
		}

		return retv;
	}
*/
	
}
