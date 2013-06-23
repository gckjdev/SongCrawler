package com.orange.songcrawler.crawler;

import java.io.IOException;

import org.htmlparser.util.ParserException;

import com.orange.common.log.ServerLog;
import com.orange.songcrawler.util.DBWriter;

public class MainCrawler {

    public static void main(String[] args) throws ParserException, IOException {
		
		String crawlerType = System.getProperty("crawler_type");
		if ( crawlerType != null && crawlerType.equalsIgnoreCase("daily")) {
		
		} 
		else if ( crawlerType != null &&  crawlerType.equalsIgnoreCase("oneshot")) {
	    	OneShotCrawler.crawlTheWholeWorld(System.getProperty("host"));
	    	String writeToDB = System.getProperty("write_db");
	    	if (writeToDB != null && Integer.parseInt(writeToDB) == 1) {
	    		DBWriter.writeAllSongsInfoToDB();
	    	}
		}
		else {
			ServerLog.info(0, "You must specify a vaild crawler type !!!"
					+ " Use -Dcrawler_type=daily or -Dcrawler_type=oneshot ");
		}
	}
}
