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
	private static List<String> errorWritingSongCollection = new ArrayList<>();
	private static final String ERROR_WRITING_SONGS_LOG = fileHierarchyBulder.getErrorWritingSongsLog();
	
	// 用于记录写song_index表时出错的条目,　以便重新写入
	private static List<String> errorWritingSongIndexCollection = new ArrayList<>();
	private static final String ERROR_WRITING_SONG_INDEX_LOG = fileHierarchyBulder.getErrorWritingSongIndexLog();
	
	// 用于记录写singer表时出错的条目,　以便重新写入
	private static List<String> errorWritingSingerCollection = new ArrayList<>();
	private static final String ERROR_WRITING_SINGERS_LOG = fileHierarchyBulder.getErrorWritingSingersLog();
		
	private static final DBAccessProxy  oneInstance = new DBAccessProxy();
	private DBAccessProxy() {}
	public static DBAccessProxy getInstance() {
		return oneInstance;
	}
	

	public void writeAllSongsInfoToDB() throws IOException {
		writeAllsongsCollections();
	}


	private void writeAllsongsCollections() throws IOException {

		for (NameCapital nc : NameCapital.values()) {
			writeSongCollectionForNameCapital(nc);
		}
		
		FileUtils.writeLines(new File(ERROR_WRITING_SONGS_LOG), "UTF-8", errorWritingSongCollection);
		FileUtils.writeLines(new File(ERROR_WRITING_SONG_INDEX_LOG), "UTF-8", errorWritingSongIndexCollection);
		FileUtils.writeLines(new File(ERROR_WRITING_SINGERS_LOG), "UTF-8", errorWritingSingerCollection);
	}

	
	public void writeSongCollectionForNameCapital(NameCapital nameCapital) {
		
		List<String> singerIndexLines = fileHierarchyBulder.parseSingerIndexFile(nameCapital.getCapital());
		if (singerIndexLines == null) {
			ServerLog.warn(0,"Failed Writing song collection for name capital: "+ nameCapital);
			errorWritingSongCollection.add(nameCapital + " : : ");
			return;
		}
		
		// 遍历该字母下的singer_index文件，依次把每个歌手歌曲写入song表
		for (String line : singerIndexLines) {
			SingerIndexLine singerIndexLine = new SingerIndexLine(line);
			String singerName = singerIndexLine.getSingerName();
			
			List<SongObjectIdMap> allSongsObjectIds = new ArrayList<>();
			List<String> songIndexLines = fileHierarchyBulder.parseSingerSongIndexFile(singerName, nameCapital.getCapital());
			if (songIndexLines == null) {
				ServerLog.warn(0,"Failed Writing song collection for singer : "+ singerName);
				errorWritingSongCollection.add(nameCapital + " : " + singerName + " : ");
				continue;
			}
			
			for (String sil : songIndexLines) {
				SongIndexLine songIndexLine = new SongIndexLine(sil);
				String songName = nameCleaner(songIndexLine.getSongName());
				String songURL = nameCleaner(songIndexLine.getSongURL());
				String songAlbum = nameCleaner(songIndexLine.getSongAlbum());
				String lyricPath = fileHierarchyBulder.getLyricFileName(singerName, nameCapital.getCapital(), songName);
				Song song = new Song(songName, songURL, songAlbum, singerName, lyricPath);

				ServerLog.info(0, "* 正在写入歌曲到数据库中: " + songName);
				ObjectId songOId = (ObjectId)songManager.writeOneSongIntoDB(song);
				if (songOId == null) {
					ServerLog.info(0, "Failed writing song collection for [" + singerName + "]'s song: " + songURL);
					errorWritingSongCollection.add(nameCapital + " : " + singerName + " : " + songURL);
					continue;
				}

				// mongodb中字段名不能含有点，　所以替换为文字表示
				allSongsObjectIds.add(new SongObjectIdMap(songName.replace(".", "_dot_"), songOId.toString()));
			}
			
			//　写完这个歌手的所有歌曲,　再为其生成一个歌曲索引表:singer_song_index
			ServerLog.info(0, "** 正在为歌手[" + singerName + "]写入song_index表...");
			ObjectId singerSongIndexOId = (ObjectId)songManager.writeSingerSongIndexCollection(singerName, allSongsObjectIds);
			if (singerSongIndexOId == null) {
				ServerLog.info(0, "Failed writing signer_song_index for singer " + singerName);
				errorWritingSongIndexCollection.add(singerName + "\n" + allSongsObjectIds.toString());
				continue;
			}
			
			// 再为这个歌手生成一个singer表
			ServerLog.info(0, "*** 正在为歌手[" + singerName + "]写入singer表...");
			ObjectId singerOId = (ObjectId)songManager.writeSingerCollection(nameCapital.getCapital(), singerName, singerSongIndexOId);
			if (singerOId == null) {
				ServerLog.info(0, "Failed writing signer_song_index for singer " + singerName);
				errorWritingSingerCollection.add(nameCapital + ":" + singerName + "\n" + allSongsObjectIds.toString());
			}
		}
	}

	private String nameCleaner(String name) {
		String result;
		result = name.replace("&#039;", "'");
		result = result.replace("&amp;", "&");
		result = result.replace("&eacute;", "é");
		result = result.replace(";", "_");
		
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