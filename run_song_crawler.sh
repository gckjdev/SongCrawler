#!/bin/bash

current_time=`date +%F_%H_%M_%S`

APP_JAR="/home/larmbr/gckj/SongCrawler/target/SongCrawler-1.0-SNAPSHOT.jar"
APP_NAME="SongCrawler"
APP_LOG="/home/larmbr/gckj/SongCrawler/log_${current_time}"

CRAWLER_TYPE="oneshot"
SONGS_DIR="/home/larmbr/songs/" 
SONG_CATEGORY_DIR="/home/larmbr/song_category/"
RUN_CMD="/bin/bash ${PWD}/`basename $0`"
LYRIC_URL_PREFIX=""
HOST="5"
DO_CATEGORIZE=0
WRITE_DB=0
MONGO_DB_ADDR="192.168.13.89"

start_app() {
    java -Dcrawler_type=${CRAWLER_TYPE} \
         -Dsongs_dir=${SONGS_DIR} \
         -Dsong_category_dir=${SONG_CATEGORY_DIR} \
         -Dcmd="${RUN_CMD}" \
         -Dlyric_url_prefix=${LYRIC_URL_PREFIX}  \
         -Dhost=${HOST} \
         -Ddo_categorize=${DO_CATEGORIZE} \
         -Dwrite_db=${WRITE_DB} \
         -Dmongodb.address=${MONGO_DB_ADDR} \
	 -jar ${APP_JAR} &> ${APP_LOG} &
     echo -e "* Starting ${APP_NAME}, log into \e[0;31m`basename ${APP_LOG}`\e[0m."
}


PID_TO_BE_KILLED=$(ps aux | grep java |  grep ${APP_NAME} | awk '{print $2}')
case "$1" in  
    "-h" | "--help" ) 
         echo "Usage:" 
         echo "       `basename "$0"`    :  start ${APP_NAME}." 
         echo "       `basename "$0"` -k :  kill  ${APP_NAME}."  
         exit 0 
         ;; 
    "" ) 
       [ ! -z ${PID_TO_BE_KILLED} ] && echo -e "* There is one instance(PID:\e[0;31m${PID_TO_BE_KILLED}\e[0m) of ${APP_NAME} running ..." && exit 1 
       start_app 
       exit 0 
       ;; 
   "-k" ) 
       [ -z ${PID_TO_BE_KILLED} ] && echo "* There is no instance of ${APP_NAME} running..." && exit 1 
       kill -15 ${PID_TO_BE_KILLED} && echo "* Killing ${APP_NAME}(PID:${PID_TO_BE_KILLED})..."  
       exit 0 
       ;; 
   "-r" ) 
       [ -z ${PID_TO_BE_KILLED} ] && echo "* There is no instance of ${APP_NAME} running..." && exit 1 
       kill -15 ${PID_TO_BE_KILLED} && echo "* Killing ${APP_NAME}(PID:${PID_TO_BE_KILLED})..."     
       start_app 
       exit 0 
       ;; 
   * ) 
       echo "* Unsupported option..." 
       exit 4 
       ;; 
esac  
