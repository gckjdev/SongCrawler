package com.orange.songcrawler.crawler;


import com.orange.common.log.ServerLog;
import com.orange.songcrawler.service.SongCategorizer;
import com.orange.songcrawler.util.DBAccessProxy;
import com.orange.songcrawler.util.PropertyConfiger;

public class MainCrawler {

    public static void main(String[] args) throws Exception {
    	   
		String crawlerType = System.getProperty("crawler_type"); // daily or oneshot
		String startCapital = System.getProperty("start_capital"); // see CrawlPolicy
		String endCapital = System.getProperty("end_capital");
		String doCategorize = System.getProperty("do_categorize"); // 1 or 0
		String writeToDB = System.getProperty("write_db"); // 1 or 0
		
		PropertyConfiger.setRunCommand(); // 读取运行此程序的命令,　以便在抓取过程中可以重启运行本程序
		PropertyConfiger.setSongsDirectory();
		PropertyConfiger.setSongCategoryDirectory();
		
		// 一次性爬虫，抓取整个歌曲库
		if ( crawlerType != null &&  crawlerType.equalsIgnoreCase("oneshot")) {
			OneShotCrawler.getInstance().crawlTheWholeWorld(startCapital, endCapital);
			return;
		}
		// 把抓取的整个歌曲库信息写入数据库
		if (writeToDB != null && Integer.parseInt(writeToDB) == 1) {
    		DBAccessProxy.getInstance().writeSongsInfoToDB(startCapital, endCapital);
    		return;
    	}
		// 抓取分类信息
		if ( doCategorize != null &&Integer.parseInt(doCategorize) == 1 ) {
			SongCategorizer.getInstance().categorizeAllSongs();
			return;
		}
		// 更新每天榜单
		if ( crawlerType != null && crawlerType.equalsIgnoreCase("daily")) {
			DailyCrawler.getInstance().crawlTopMusic();
			return;
		} 
		else {
			ServerLog.info(0, "You must specify a vaild crawler type or specify do_categorize !!!");
			return;
		}
	}
}
