package com.orange.songcrawler.util;

import java.util.HashMap;
import java.util.Map;

import com.orange.common.log.ServerLog;

public class ArgumentParser {

	
	public enum Arguments {
		CRAWLER_TYPE("crawler_type") ,  // 一次性爬虫还是每天都运行的爬虫
		START_CAPITAL("start_capital"), // 一次性爬虫抓取的字母范围的起始字母
		END_CAPITAL("end_capital"),     // 一次性爬虫抓取的字母范围的终点字母
		WRITE_DB("write_db"),           // 把歌曲库写入数据库,1: 是，0：否
		DO_CATEGORIZE("do_categorize"), // 抓取分类数据,1: 是，0：否
		SONGS_DIR("songs_dir"),          // 歌曲信息目录
		SONG_CATEGORY_DIR("song_category_dir"), // 分类信息目录 
		EXTRA_DB("extra_db"), // 生成额外的数据库表（singer表和singer_song_index表),1: 是，0：否
		UPDATE_ALL_SONGS("update_all_songs"), // 更新整个歌曲库
		;
		
		
		private static Map<String, String> argPairs = new HashMap<String, String>();
		private final String argName;
		
		private Arguments(String argName){
			this.argName = argName;
		}
		
		private String setArg() {
			return System.getProperty(argName);
		}

		public String getValue(){

			if (argName == null)
				return null;
			
			String result = argPairs.get(argName);
			if (result == null || result.equals(""))
				return null;
			
			return result;
		}
		
		static {
			ServerLog.info(0,"Setting all command line arguments...");
			for (Arguments arg: Arguments.values()) {
				argPairs.put(arg.argName, arg.setArg());
			}
		}

	}
	
	public static boolean writeExtraDBCollection() {
		
		String extraDB = Arguments.EXTRA_DB.getValue();
		if (extraDB != null && extraDB.equals("1"))
			return true;
		return false;
	}

	public static boolean doOneShotCrawl() {
		String crawlerType = Arguments.CRAWLER_TYPE.getValue();
		if (crawlerType != null &&  crawlerType.equalsIgnoreCase("oneshot"))
	        return true;
		return false;
	}

	public static boolean doDailyCrawl() {
		String crawlerType = Arguments.CRAWLER_TYPE.getValue();
		if (crawlerType != null &&  crawlerType.equalsIgnoreCase("daily"))
	        return true;
		return false;
	}

	public static boolean writeToDB() {
		String writeToDB = Arguments.WRITE_DB.getValue();
		if (writeToDB != null && writeToDB.equals("1")) 
			return true;
		return false;
	}

	public static boolean doCategorize() {
		String doCategorize = Arguments.DO_CATEGORIZE.getValue();
		if (doCategorize != null && doCategorize.equals("1"))
			return true;
		return false;
	}

	public static boolean updateAllSongs() {
		String updateAllSongs = Arguments.UPDATE_ALL_SONGS.getValue();
		if (updateAllSongs != null && updateAllSongs.equals("1"))
			return true;
		return false;
	}
}
