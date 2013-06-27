package com.orange.songcrawler.crawler;


import com.orange.common.log.ServerLog;
import com.orange.songcrawler.service.SongCategorizer;
import com.orange.songcrawler.util.DBAccessProxy;
import com.orange.songcrawler.util.PropertyConfiger;

public class MainCrawler {

    public static void main(String[] args) throws Exception {
		
		String crawlerType = System.getProperty("crawler_type"); // daily or oneshot
		String host = System.getProperty("host");
		String doCategorize = System.getProperty("do_categorize"); // 1 or 0
		String writeToDB = System.getProperty("write_db");
		
		PropertyConfiger.setRunCommand(); // 读取运行此程序的命令,　以便在抓取过程中可以重启运行本程序
		PropertyConfiger.setSongsDirectory();
		PropertyConfiger.setSongsDirectory();
		
		if ( crawlerType != null && crawlerType.equalsIgnoreCase("daily")) {
		
		} 
		else if ( crawlerType != null &&  crawlerType.equalsIgnoreCase("oneshot")) {
	    	OneShotCrawler.getInstance().crawlTheWholeWorld(host);
	    	if (writeToDB != null && Integer.parseInt(writeToDB) == 1) {
	    		DBAccessProxy.getInstance().writeAllSongsInfoToDB();
	    	}
		}
		else if ( doCategorize != null &&Integer.parseInt(doCategorize) == 1 ) {
			SongCategorizer.getInstance().categorizeAllSongs();
		}
		else {
			ServerLog.info(0, "You must specify a vaild crawler type or specify do_categorize !!!");
		}
	}
}
