package com.orange.songcrawler.util;

public class PropertyConfiger {

	private static String cmd = null;
	private static String songsDir = null;
	private static String songCategoryDir = null;

	/**
	 * 由于百度对ＩＰ访问效率有限制,如果在抓取过程中超时没结果,
	 * 将重新启动该程序. 
	 */
	public static void setRunCommand() throws Exception {
		cmd = System.getProperty("cmd");
		if (cmd == null || cmd.equals("")) {
			throw new Exception("You should specify the run command!");
	    }
	}
	
	public static String getRunCommand() {
		return cmd;
	}

	
	public static void setSongsDirectory() throws Exception {
		songsDir  = System.getProperty("songs_dir");
		if (songsDir == null || songsDir.equals("")) {
			throw new Exception("You should specify the songs directory !");
	    }
	}
	
	public static String getSongsDirectory() {
		return songsDir;
	}
	
	
	public static void setSongCategoryDirectory() throws Exception {
		songCategoryDir   = System.getProperty("song_category_dir");
		if (songCategoryDir == null || songCategoryDir.equals("")) {
			throw new Exception("You should specify the song category directory !");
	    }
	}
	
	public static String getSongCategoryDirectory() {
		return songCategoryDir;
	}
		
}
