package leon.bets;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Parser implements Runnable {
	
	private String matchId;
	private final static int MAX_THREAD_POOL = 3;
	
	private Parser(String id) {
		this.matchId = id;
	}
	
	private static JsonElement getJsonElem(String sUrl) {
		
		JsonElement obj = null;
		
		try {
			URL url = new URL(sUrl);
			URLConnection request = url.openConnection();
			request.connect();
			InputStreamReader reader = new InputStreamReader((InputStream) request.getContent());
			obj = JsonParser.parseReader(reader);
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		return obj;
	}
	
	public static void parse() {
		
		String sUrl;
		ArrayList<String> topLeagueIdList = getTopLeaguesIdList();
		ArrayList<String> matchesIdList = getMatchesIdList(topLeagueIdList);
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREAD_POOL);
		
		for (String id : matchesIdList) {
			Parser p = new Parser(id);
			executor.execute(p);
		}
		executor.shutdown();
				
	}
	
	private static void printJSON(JsonObject obj) {
		String res = "";
		String sport = obj.get("league").getAsJsonObject().get("sport").getAsJsonObject().get("name").getAsString();
		String country = obj.get("league").getAsJsonObject().get("region").getAsJsonObject().get("name").getAsString();
		String league = obj.get("league").getAsJsonObject().get("name").getAsString();
		String name = obj.get("name").getAsString();
		ZonedDateTime kickoff = Instant.ofEpochMilli(obj.get("kickoff").getAsLong()).atZone(ZoneOffset.UTC);
		String startTime = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss z").format(kickoff);
		String id = obj.get("id").getAsString();
		res += String.format("%s, %s %s\n", sport, country, league);
		res += String.format("\t%s, %s, %s\n", name, startTime, id);
		JsonArray markets = obj.get("markets").getAsJsonArray();
		String lastRunner = "";
		for (int i = 0; i < markets.size(); i++) {
			String runner = markets.get(i).getAsJsonObject().get("name").getAsString();
			if (runner.compareTo(lastRunner) != 0) {
				lastRunner = runner;
				res += String.format("\t\t%s\n", runner);
			}
			JsonArray tmpObj = markets.get(i).getAsJsonObject().get("runners").getAsJsonArray();
			for (int j = 0; j < tmpObj.size(); j++) {
				String rName = tmpObj.get(j).getAsJsonObject().get("name").getAsString();
				String rPrice = tmpObj.get(j).getAsJsonObject().get("price").getAsString();
				String rId = tmpObj.get(j).getAsJsonObject().get("id").getAsString();
				res += String.format("\t\t\t%s, %s, %s\n", rName, rPrice, rId);
			}
		}

		System.out.println(res);
	}
	
	private static ArrayList<String> getTopLeaguesIdList() {
		String sUrl = "https://leon.bet/api-2/betline/sports?ctag=en-US&flags=urlv2";
		ArrayList<String> leaguesIdList = new ArrayList<String>();
		ArrayList<String> sportIdList = new ArrayList<String>();
		sportIdList.add("1970324836974595");/*Football*/
		sportIdList.add("1970324836974602");/*Ice Hockey*/
		sportIdList.add("1970324836974594");/*Tennis*/
		sportIdList.add("1970324836974598");/*Basketball*/
		
		JsonArray gamesArr = getJsonElem(sUrl).getAsJsonArray();
		
		for (int i = 0; i < gamesArr.size(); i++) {
			JsonObject tmpObj = gamesArr.get(i).getAsJsonObject();
			if (sportIdList.contains(tmpObj.get("id").getAsString())) {
				JsonArray regionsArr = tmpObj.get("regions").getAsJsonArray();
				for (int j = 0; j < regionsArr.size(); j++) {
					JsonArray leaguesArr = regionsArr.get(j).getAsJsonObject().get("leagues").getAsJsonArray();
					for (int k = 0; k < leaguesArr.size(); k++) {
						JsonObject leagueObj = leaguesArr.get(k).getAsJsonObject();
						if (leagueObj.get("top").getAsBoolean())
							leaguesIdList.add(leagueObj.get("id").getAsString());
					}
				}
			}
		}
		
		return leaguesIdList;
	}
	
	private static ArrayList<String> getMatchesIdList(ArrayList<String> leaguesIdList) {
		ArrayList<String> matchesIdList = new ArrayList<String>();
		String sUrl;
		
		for (String leagueId : leaguesIdList) {
			sUrl = String.format(
					"https://leon.bet/api-2/betline/changes/all?ctag=en-US&vtag=9c2cd386-31e1-4ce9-a140-28e9b63a9300&league_id=%s&hideClosed=true&flags=reg,mm2,rrc,nodup,urlv2", 
					leagueId);
			JsonObject matchesObj = getJsonElem(sUrl).getAsJsonObject();
			matchesIdList.add(matchesObj
					.get("data")
					.getAsJsonArray()
					.get(0)
					.getAsJsonObject()
					.get("id")
					.getAsString());
		}
		
		return matchesIdList;
	}
	
	public void run() {
		String sUrl = String.format(
				"https://leon.bet/api-2/betline/event/all?ctag=en-US&eventId=%s&flags=reg,mm2,rrc,nodup,urlv2,smg,outv2", 
				this.matchId);
		JsonObject obj = getJsonElem(sUrl).getAsJsonObject();
		printJSON(obj);		
	}
}
