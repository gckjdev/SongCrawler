package com.orange.songcrawler.crawler;


import com.orange.common.log.ServerLog;
import com.orange.songcrawler.service.SongCategorizer;
import com.orange.songcrawler.util.ArgumentParser;
import com.orange.songcrawler.util.ArgumentParser.Arguments;
import com.orange.songcrawler.util.DBAccessProxy;

public class MainCrawler {

    public static void main(String[] args) throws Exception {
		
		// 一次性爬虫，抓取整个歌曲库
    	// Note:
    	// 1. 该方法可断点续抓
    	// 2. 可定期运行该方法更新整个歌曲库，只需多传入-Dupdate_all_songs=1,即可
		if (ArgumentParser.doOneShotCrawl()) {
			OneShotCrawler.getInstance().crawlTheWholeWorld(Arguments.START_CAPITAL.getValue(),
					Arguments.END_CAPITAL.getValue());
			return;
		}
		// 把抓取的整个歌曲库信息写入数据库(该方法可中断重运行)
		if (ArgumentParser.writeToDB()) {
    		DBAccessProxy.getInstance().writeSongsInfoToDB(Arguments.START_CAPITAL.getValue(),
					Arguments.END_CAPITAL.getValue());
    		return;
    	}
		// 抓取分类信息
		if (ArgumentParser.doCategorize()) {
			SongCategorizer.getInstance().categorizeAllSongs();
			return;
		}
		// 更新每天榜单
		if (ArgumentParser.doDailyCrawl()) {
			DailyCrawler.getInstance().crawlTopMusic();
			return;
		} 
		else {
			ServerLog.info(0, "You must specify a vaild operation !!!");
			return;
		}
	}
}
