package com.orange.songcrawler.api;

import com.orange.common.log.ServerLog;

public class MainCrawler {

    public static void main(String[] args) {
		
		String crawlerType = System.getProperty("crawler_type");
		if ( crawlerType != null && crawlerType.equalsIgnoreCase("daily")) {
		
		} 
		else if ( crawlerType != null &&  crawlerType.equalsIgnoreCase("oneshot")) {
		
		}
		else {
			ServerLog.info(0, "You must specify a vaild crawler type !!!"
					+ " Use -Dcrawler_type=daily or -Dcrawler_type=oneshot ");
		}
	}
	
	
}
