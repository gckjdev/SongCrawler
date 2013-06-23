package com.orange.songcrawler.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class FileHierarchyBuilder {
	
	private static final String SONGS_INFO_DIR = "/home/larmbr/songs/";
	private static int singerIndexFileHeaderLines = 5;
	private static int songIndexFileHeaderLines = 5;

	/*
	 * 定义singer_index文件的行格式
	 * 所有歌曲分Ａ-Ｚ共26个大目录,　每个目录下有所以以这个字母作名字首字母的歌手子目录
	 * 和一个singer_index文件, 其格式为:
	 * 
	 * 　　歌手名 || 歌手链接URL
	 */
	public static class SingerIndexLine {
		
		private final String singerName;
		private final String singerURL;
		
		public SingerIndexLine(String line) {
			String[] splittedLine = line.split("\\|\\|"); // "||"需要转义
			this.singerName = splittedLine[0].trim();
			this.singerURL = splittedLine[1].trim();
		}
		
		public String getSingerName() {
			return singerName;
		}
		
		public String getSingerURL() {
			return singerURL;
		}

		public static String buildLine(String singerName, String url) {
			return singerName + " || " + url;
		}
	}
	
	
	/*
	 * 定义song_index文件的行格式
	 * 所有歌曲分Ａ-Ｚ共26个大目录,　每个目录下有所以以这个字母作名字首字母的歌手子目录
	 * 和一个singer_index文件, 每个歌手子目录下有该歌手所有歌的歌词文件和song_index
	 * 文件, 其格式为:
	 * 
	 * 　　歌曲名 || 歌曲链接URL　|| 歌曲所属专辑(可选)
	 */
	public static class SongIndexLine {
		
		private final String songName;
		private final String songURL;
		private final String songAlbum;
		
		public SongIndexLine(String line) {
			String[] splittedLine = line.split("\\|\\|"); // "||"需要转义
			this.songName = splittedLine[0].trim();
			this.songURL = splittedLine[1].trim();
			this.songAlbum = splittedLine[2].trim();
		}

		public String getSongName() {
			return songName;
		}

		public String getSongURL() {
			return songURL;
		}

		public String getSongAlbum() {
			return songAlbum;
		}

		public static String buildLine(String songName, String songUrl, String album) {
			return songName + " || "  + songUrl + " || " + album;
		}
	}
	
	
	public static void writeSingerIndexFile(String nameCapital, List<String> singerUrls) throws IOException {

		int singerNum = singerUrls.size();
		List<String> lines = new ArrayList<String>();
		
		//　头部 
		lines.add("---");
		lines.add("首字母: " + nameCapital);  // 注意! 英文的冒号和空格
		lines.add("歌手总数: " + singerNum); // 注意! 英文的冒号和空格
		lines.add("---");
		lines.add(""); //空行,换行符WriteLines会帮我们添加
		
		//　主体
        lines.addAll(singerUrls);
        
		// 写入
		String pathName = getSingerIndexFileName(nameCapital);
		FileUtils.writeLines(new File(pathName), "UTF-8", lines);
	}

	public static List<String> parseSingerIndexFile(String nameCapital) {
		
		List<String> restult = null;
		
		List<String> lines= readFileIntoLines(getSingerIndexFileName(nameCapital));
		try {
			restult = new ArrayList<String>(lines.subList(singerIndexFileHeaderLines, lines.size()));
		} catch (Exception e) {
			restult = null;
		}
		
		return restult;
	}
	
	
	
	public static void writeSingerSongIndexFile(String singerName, String nameCapital,
			                                    int totalSongs, List<String> songData) throws IOException {

		if (singerName == null) 
			singerName = "未知";
		if (songData == null)
			songData = Collections.emptyList();
		
		List<String> lines = new ArrayList<String>();
		
		//　头部 
		lines.add("---");
		lines.add("歌手: " +singerName);  // 注意! 英文的冒号和空格
		lines.add("总数: " + totalSongs); // 注意! 英文的冒号和空格
		lines.add("---");
		lines.add(""); //空行,换行符WriteLines会帮我们添加
		
		//　主体
		lines.addAll(songData);
		
		// 写入
		String pathName = getSingerSongIndexFileName(singerName, nameCapital);
		FileUtils.writeLines(new File(pathName), "UTF-8", lines);
	}

	
    public static List<String> parseSingerSongIndexFile(String singerName, String nameCapital) {
		
		List<String> restult = null;
		
		List<String> lines= readFileIntoLines(getSingerSongIndexFileName(singerName, nameCapital));
		try {
			restult = new ArrayList<String>(lines.subList(songIndexFileHeaderLines, lines.size()));
		} catch (Exception e) {
			restult = null;
		}
		
		return restult;
	}
	
	
    public static String getSingerIndexFileName(final String nameCapital) {
		
		String singerIndexFileName = SONGS_INFO_DIR + nameCapital + "/singer_index";
		return singerIndexFileName;
	}


    public static String getSingerSongIndexFileName(final String singerName, final String nameCapital) {
		
    	String songsIndexFileName = SONGS_INFO_DIR + nameCapital + "/" + singerName.replaceAll("\\s+", "_")
    			                          + "/song_index";
		return songsIndexFileName;
	}
    
    
    public static String getLyricFileName(final String singerName,final String nameCapital, final String songName) {
		
		String songLyricFileName = SONGS_INFO_DIR + nameCapital + "/" + singerName.replaceAll("\\s+", "_") 
				                              + "/" + songName.replaceAll("\\s+", "_") + ".lyric";
		return songLyricFileName;
	}
	
    
    @SuppressWarnings("unchecked")
	public static List<String> readFileIntoLines(String pathName) {

		File file = new File(pathName);
		List<String> lines = null;  
		if (file.canRead()) {
			try {
				lines = FileUtils.readLines(file,"UTF-8");
			} catch (IOException e) {
				lines = null;
			}
		}
		
		return lines;
	}


	
    
    public static String getErrorCrawlingSingersURLLog() {
		return SONGS_INFO_DIR + "error_crawling_singers_url.log";
	}


	public static String getErrorCrawlingSongsURLLog() {
		return SONGS_INFO_DIR + "error_crawling_songs_url.log";
	}


	public static String getErrorWritingSingersLog() {
		return SONGS_INFO_DIR + "error_writing_singers.log";
	}
	

	public static String getErrorWritingSongsLog() {
		return SONGS_INFO_DIR + "error_writing_songs.log";
	}
	
	public static String getErrorWritingSongIndexLog() {
		return SONGS_INFO_DIR + "error_writing_song_index.log";
	}
	
	public static String getErrorCrawlingLyricsLog() {
		return SONGS_INFO_DIR + "error_crawling_songs_lyrics.log";
	}


	public static String getTmpFile(String nameCapital, String singerName, int index) {
		return SONGS_INFO_DIR + nameCapital + "/" + singerName +  "/" + singerName + "_" + index + ".html";
	}


}