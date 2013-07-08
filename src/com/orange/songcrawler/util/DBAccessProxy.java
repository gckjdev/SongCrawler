package com.orange.songcrawler.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;

import com.orange.common.log.ServerLog;
import com.orange.game.model.dao.song.Song;
import com.orange.game.model.dao.song.SongCategory;
import com.orange.game.model.manager.song.SongCategoryManager;
import com.orange.game.model.manager.song.SongManager;
import com.orange.game.model.manager.song.SongManager.SongObjectIdMap;
import com.orange.songcrawler.service.CrawlPolicy.NameCapital;
import com.orange.songcrawler.util.FileHierarchyBuilder.SingerIndexLine;
import com.orange.songcrawler.util.FileHierarchyBuilder.SongIndexLine;

public class DBAccessProxy {

	private static final SongManager songManager = SongManager.getInstance();
	private static final SongCategoryManager  songCategoryManager = SongCategoryManager.getInstance();
	private static final FileHierarchyBuilder fileHierarchyBulder = FileHierarchyBuilder.getInstance();
	
    // 用于记录写song表时出错的条目,　以便根据这些条目,重新尝试写入:
	// 如果在解析singer_index就出错,　则条目格式为:　name capital :  :
	// 如果在写入某个singer时出错,　则条目格式为:　　 name capital : singer :
	// 如果在写入某首歌时出错,　则条目格式为:　       name capital : singer : song URL
	private static List<String> errorWritingSongCollection = new ArrayList<String>();
	private static final String ERROR_WRITING_SONGS_LOG = fileHierarchyBulder.getErrorWritingSongsLog();
	
	// 用于记录写song_index表时出错的条目,　以便重新写入
	private static List<String> errorWritingSongIndexCollection = new ArrayList<String>();
	private static final String ERROR_WRITING_SONG_INDEX_LOG = fileHierarchyBulder.getErrorWritingSongIndexLog();
	
	// 用于记录写singer表时出错的条目,　以便重新写入
	private static List<String> errorWritingSingerCollection = new ArrayList<String>();
	private static final String ERROR_WRITING_SINGERS_LOG = fileHierarchyBulder.getErrorWritingSingersLog();
		
	private static final DBAccessProxy  oneInstance = new DBAccessProxy();
	private DBAccessProxy() {}
	public static DBAccessProxy getInstance() {
		return oneInstance;
	}
	

	public void writeSongsInfoToDB(String startCapital, String endCapital) throws IOException {
		
		// 第一步：把所有歌曲写入song表
		writeSongsCollections(startCapital, endCapital);
		
		if (ArgumentParser.writeExtraDBCollection()) {
			// 第二步（可选）：为所有歌手创建singer_song_index表
		    writeSingerSongIndexCollections(startCapital, endCapital);
		
		    // 第三步（可选）：为所有歌手创建singer表
		    writeSingerCollections(startCapital, endCapital);
		}
	}


	private void writeSongsCollections(String startCapital, String endCapital) throws IOException {

		for (NameCapital nc : NameCapital.values()) {
			if ( nc.ordinal() < NameCapital.valueOf(startCapital).ordinal() ||
					nc.ordinal() > NameCapital.valueOf(endCapital).ordinal() )
				// 不在范围内的跳过
				continue;
			
			writeSongCollectionForNameCapital(nc);
		}
		
		// 完成后把出错的条目写入日志文件
		FileUtils.writeLines(new File(ERROR_WRITING_SONGS_LOG), "UTF-8", errorWritingSongCollection);
	}

	
	private void writeSingerSongIndexCollections(String startCapital,String endCapital) throws IOException {
		
		for (NameCapital nc : NameCapital.values()) {
			if ( nc.ordinal() < NameCapital.valueOf(startCapital).ordinal() ||
					nc.ordinal() > NameCapital.valueOf(endCapital).ordinal() )
				// 不在范围内的跳过
				continue;
		
			WriteSongIndexCollectionForNameCapital(nc);
		}
		
		// 完成后把出错的条目写入日志文件
		FileUtils.writeLines(new File(ERROR_WRITING_SONG_INDEX_LOG), "UTF-8", errorWritingSongIndexCollection);
	}
	
	
	private void writeSingerCollections(String startCapital,String endCapital) throws IOException {
		
		for (NameCapital nc : NameCapital.values()) {
			if ( nc.ordinal() < NameCapital.valueOf(startCapital).ordinal() ||
					nc.ordinal() > NameCapital.valueOf(endCapital).ordinal() )
				// 不在范围内的跳过
				continue;
		
			WriteSingerCollectionForNameCapital(nc);
		}
		
		// 完成后把出错的条目写入日志文件
		FileUtils.writeLines(new File(ERROR_WRITING_SINGERS_LOG), "UTF-8", errorWritingSingerCollection);
	}
	
	
    private void writeSongCollectionForNameCapital(NameCapital nameCapital) {
		
		List<String> singerIndexLines = fileHierarchyBulder.parseSingerIndexFile(nameCapital.getCapital());
		if (singerIndexLines == null) {
			ServerLog.warn(0,"Failed Writing song collection for name capital: "+ nameCapital);
			errorWritingSongCollection.add(nameCapital + " : : ");
			return;
		}
		
		// 遍历该字母下的singer_index文件
		for (String line : singerIndexLines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();
			
			// 遍历该歌手的song_index文件,把每一首歌曲写入song表
			List<String> songIndexLines = fileHierarchyBulder.parseSingerSongIndexFile(singerName, nameCapital.getCapital());
			if (songIndexLines == null) {
				ServerLog.warn(0,"Failed Writing song collection for singer : "+ singerName);
				errorWritingSongCollection.add(nameCapital + " : " + singerName + " : ");
				continue;
			}
			
			int sleepCount = 0;
			for (String sil : songIndexLines) {
				SongIndexLine songIndexLine = new SongIndexLine(sil);
				String songName = nameCleaner(songIndexLine.getSongName());
				String songURL = songIndexLine.getSongURL();
				String songAlbum = nameCleaner(songIndexLine.getSongAlbum());
				String lyricPath = fileHierarchyBulder.getLyricFileName(singerName, nameCapital.getCapital(), songName);
				Song song = new Song(songName, songURL, songAlbum, singerName, lyricPath);

				ServerLog.info(0, "* 正在写入歌曲到数据库中: [" + songName + "]（ nameCapital: "+nameCapital + ", singer : " + singerName +" )");
				songManager.writeOneSongIntoDB(song);

				// 速率控制
				sleepCount++;
				doSleepIfNeeded(sleepCount);
			}
		}
	}


   private void WriteSongIndexCollectionForNameCapital(NameCapital nameCapital) {
	
		List<String> singerIndexLines = fileHierarchyBulder
				.parseSingerIndexFile(nameCapital.getCapital());
		if (singerIndexLines == null) {
			ServerLog.warn(0, "Failed Writing singer_song_index collection for name capital: " + nameCapital);
			errorWritingSongIndexCollection.add(nameCapital + " : : ");
			return;
		}

		// 遍历该字母下的singer_index文件
		int sleepCount = 0;
		for (String line : singerIndexLines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();

			// 遍历该歌手的song_index文件
			List<String> songIndexLines = fileHierarchyBulder.parseSingerSongIndexFile(singerName, nameCapital.getCapital());
			if (songIndexLines == null) {
				ServerLog.warn(0,"Failed Writing singer_song_index collection for singer : " + singerName);
				errorWritingSongIndexCollection.add(nameCapital + " : " + singerName + " : ");
				continue;
			}

			List<Song> songs = new ArrayList<Song>();
			for (String sil : songIndexLines) {
				SongIndexLine songIndexLine = new SongIndexLine(sil);
				String songName = nameCleaner(songIndexLine.getSongName());
				songs.addAll(songManager.findSongBySongNameAndSinger(songName, singerName));
			}

			List<SongObjectIdMap> allSongsObjectIds = new ArrayList<SongObjectIdMap>();
			for (Song song : songs) {
				String songName = song.getSongName() == null ? "" : song.getSongName();
				allSongsObjectIds.add(new SongObjectIdMap(songName,song.getObjectId().toString()));
			}

			ServerLog.info(0, "** 正在为歌手[" + singerName + "]写入song_index表...");
			songManager.writeSingerSongIndexCollection(singerName, allSongsObjectIds);

			// 速率控制
			sleepCount++;
			doSleepIfNeeded(sleepCount);
	    }
    }
  

	private void WriteSingerCollectionForNameCapital(NameCapital nameCapital) {
		
		List<String> singerIndexLines = fileHierarchyBulder.parseSingerIndexFile(nameCapital.getCapital());
		if (singerIndexLines == null) {
			ServerLog.warn(0,"Failed Writing singer collection for name capital: "+ nameCapital);
			errorWritingSingerCollection.add(nameCapital + " : : ");
			return;
		}
		
		// 遍历该字母下的singer_index文件
		int sleepCount = 0;
		for (String line : singerIndexLines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();
			
			ObjectId singerSongIndexOId = songManager.findSingerSongIndexOId(singerName);
			
			ServerLog.info(0, "*** 正在为歌手[" + singerName + "]写入singer表...");
			songManager.writeSingerCollection(nameCapital.getCapital(), singerName, singerSongIndexOId);
			
			// 速率控制
			sleepCount++;
			doSleepIfNeeded(sleepCount);
		}
	}
	
	   
    private void doSleepIfNeeded(int sleepCount) {
    	
		if (sleepCount % 10 == 0) {
			try {
				int sleep_interval_second = 2;
				ServerLog.info(0, "睡眠" + sleep_interval_second + "秒钟zzzZZZ~~~~~~");
				Thread.sleep(sleep_interval_second * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

    
	private String nameCleaner(String name) {
		String result;
		result = name.replace("&#039;", "'");
		result = result.replace("&amp;", "&");
		result = result.replace("&eacute;", "é");
		result = result.replace(";", "_");
		result = result.replace("/", "_");
		
		return result;
	}
	
	
	public void updateCategoryInfoForSong(Song song, String category, String subcategory) {
		songManager.updateCategoryInfoForSong(song, category, subcategory);
	}


	public List<Song> findSongBySongNameAndSinger(String songName, String singerName) {
		return songManager.findSongBySongNameAndSinger(songName, singerName);
	}


	public void writeSongCategoryCollection(String category,String subcategory, 
		      Map<String, String> songsInThisCategory) {
		
		SongCategory songCategory = new SongCategory(category, subcategory, songsInThisCategory);
		songCategoryManager.writeSongCategoryCollection(songCategory);
	}

}